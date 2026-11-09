package dev.cipherchannels.storage;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OsSecretStoreTest {
    @Test
    void windowsUsesLocalGenericCredentialsAndFreesReadBuffers() {
        assertEquals(Native.POINTER_SIZE == 8 ? 80 : 52, new OsSecretStore.Credential().size());
        FakeWindows nativeApi = new FakeWindows();
        OsSecretStore.Windows store = new OsSecretStore.Windows("dev.cipherchannels.test", nativeApi);
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        assertNull(store.load("channel"));
        store.save("channel", key);
        assertEquals("dev.cipherchannels.test/channel", nativeApi.target);
        assertEquals(1, nativeApi.type);
        assertEquals(2, nativeApi.persist);
        assertArrayEquals(key, store.load("channel"));
        assertEquals(1, nativeApi.freed);
        store.delete("channel");
        store.delete("channel");
        assertNull(store.load("channel"));
    }

    @Test
    void windowsDoesNotTreatAccessDeniedAsAMissingCredential() {
        FakeWindows nativeApi = new FakeWindows();
        nativeApi.error = 5;
        OsSecretStore.Windows store = new OsSecretStore.Windows("test", nativeApi);
        assertThrows(IllegalStateException.class, () -> store.load("channel"));
        assertThrows(IllegalStateException.class, () -> store.save("channel", new byte[32]));
        assertThrows(IllegalStateException.class, () -> store.delete("channel"));
    }

    @Test
    void linuxUsesTheDefaultPersistentCollectionAndReleasesPasswordsAndSchemas() {
        FakeLinux nativeApi = new FakeLinux();
        OsSecretStore.Linux store = new OsSecretStore.Linux("dev.cipherchannels.test", nativeApi, error -> {});
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 9);
        assertNull(store.load("channel"));
        store.save("channel", key);
        assertNull(nativeApi.collection);
        assertEquals(Base64.getEncoder().encodeToString(key), nativeApi.password);
        assertArrayEquals(key, store.load("channel"));
        assertEquals(1, nativeApi.freed);
        store.delete("channel");
        store.delete("channel");
        assertNull(store.load("channel"));
        assertEquals(nativeApi.schemas, nativeApi.unrefs);
    }

    @Test
    void linuxRejectsShortMalformedAndOversizedNativeSecretsWithoutLeakingBuffers() {
        for (String invalid : new String[] {"", "short", "!".repeat(44), "A".repeat(200)}) {
            FakeLinux nativeApi = new FakeLinux();
            nativeApi.password = invalid;
            OsSecretStore.Linux store = new OsSecretStore.Linux("test", nativeApi, error -> {});
            assertThrows(RuntimeException.class, () -> store.load("channel"));
            assertEquals(1, nativeApi.freed);
            assertEquals(nativeApi.schemas, nativeApi.unrefs);
        }
    }

    @Test
    void linuxReportsErrorsAndDoesNotClaimToDeleteALockedItem() {
        FakeLinux nativeApi = new FakeLinux();
        int[] freedErrors = {0};
        OsSecretStore.Linux store = new OsSecretStore.Linux("test", nativeApi, error -> freedErrors[0]++);
        nativeApi.fail = true;
        assertThrows(IllegalStateException.class, () -> store.load("channel"));
        assertThrows(IllegalStateException.class, () -> store.save("channel", new byte[32]));
        assertThrows(IllegalStateException.class, () -> store.delete("channel"));
        assertEquals(3, freedErrors[0]);
        nativeApi.fail = false;
        nativeApi.locked = true;
        nativeApi.password = Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(IllegalStateException.class, () -> store.delete("channel"));
    }

    private static final class FakeWindows implements OsSecretStore.Credentials {
        byte[] value;
        String target;
        int type;
        int persist;
        int freed;
        int error;
        Memory returned;
        OsSecretStore.Credential credential;

        @Override public boolean CredReadW(WString targetName, int credentialType, int flags, PointerByReference result) {
            if (error != 0 || value == null) {
                Native.setLastError(error != 0 ? error : 1168);
                return false;
            }
            returned = new Memory(value.length);
            returned.write(0, value, 0, value.length);
            credential = new OsSecretStore.Credential();
            credential.blob = returned;
            credential.blobSize = value.length;
            credential.write();
            result.setValue(credential.getPointer());
            return true;
        }

        @Override public boolean CredWriteW(OsSecretStore.Credential value, int flags) {
            if (error != 0) { Native.setLastError(error); return false; }
            target = value.targetName.toString();
            type = value.type;
            persist = value.persist;
            this.value = value.blob.getByteArray(0, value.blobSize);
            return true;
        }

        @Override public boolean CredDeleteW(WString targetName, int credentialType, int flags) {
            if (error != 0 || value == null) {
                Native.setLastError(error != 0 ? error : 1168);
                return false;
            }
            Arrays.fill(value, (byte) 0);
            value = null;
            return true;
        }

        @Override public void CredFree(Pointer pointer) {
            returned.clear();
            returned.close();
            freed++;
        }
    }

    private static final class FakeLinux implements OsSecretStore.LibSecret {
        String password;
        String collection;
        int freed;
        int schemas;
        int unrefs;
        boolean fail;
        boolean locked;
        Memory returned;

        @Override public Pointer secret_schema_new(String name, int flags, Object... attributes) {
            schemas++;
            assertEquals("account", attributes[0]);
            assertEquals(0, attributes[1]);
            assertNull(attributes[2]);
            return Pointer.createConstant(1);
        }

        @Override public void secret_schema_unref(Pointer schema) { unrefs++; }

        private boolean error(PointerByReference error) {
            if (fail) error.setValue(Pointer.createConstant(2));
            return fail;
        }

        @Override public Pointer secret_password_lookup_sync(Pointer schema, Pointer cancellable,
                                                             PointerByReference error, Object... attributes) {
            assertEquals("account", attributes[0]);
            assertEquals("channel", attributes[1]);
            assertNull(attributes[2]);
            if (error(error) || password == null) return null;
            byte[] bytes = password.getBytes(StandardCharsets.US_ASCII);
            returned = new Memory(bytes.length + 1L);
            returned.clear();
            returned.write(0, bytes, 0, bytes.length);
            return returned;
        }

        @Override public int secret_password_store_sync(Pointer schema, String targetCollection, String label,
                                                        Pointer value, Pointer cancellable, PointerByReference error,
                                                        Object... attributes) {
            if (error(error)) return 0;
            collection = targetCollection;
            password = value.getString(0, "US-ASCII");
            return 1;
        }

        @Override public int secret_password_clear_sync(Pointer schema, Pointer cancellable,
                                                        PointerByReference error, Object... attributes) {
            if (error(error) || locked) return 0;
            password = null;
            return 1;
        }

        @Override public void secret_password_free(Pointer pointer) {
            returned.clear();
            returned.close();
            freed++;
        }
    }
}
