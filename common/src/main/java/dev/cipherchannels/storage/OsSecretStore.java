package dev.cipherchannels.storage;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import dev.cipherchannels.crypto.ChannelIdentity;
import dev.cipherchannels.crypto.KeyMaterial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class OsSecretStore implements SecretStore {
    private final String namespace;
    private Backend backend;

    public OsSecretStore(Path configDirectory) {
        byte[] digest = ChannelIdentity.sha256(configDirectory.toAbsolutePath().normalize().toString()
            .getBytes(StandardCharsets.UTF_8));
        namespace = "dev.cipherchannels." + HexFormat.of().formatHex(digest);
    }

    @Override public byte[] load(UUID channelId) {
        return backend().load(channelId.toString());
    }

    @Override public void save(UUID channelId, byte[] key) {
        if (key.length != KeyMaterial.LENGTH) throw unavailable();
        backend().save(channelId.toString(), key);
    }

    @Override public void delete(UUID channelId) {
        backend().delete(channelId.toString());
    }

    private synchronized Backend backend() {
        try {
            if (backend == null) {
                if (Platform.isMac()) backend = new Mac(namespace);
                else if (Platform.isWindows()) backend = new Windows(namespace);
                else if (Platform.isLinux()) backend = new Linux(namespace);
                else throw unavailable();
            }
            return backend;
        } catch (LinkageError | RuntimeException exception) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("OS credential storage is unavailable");
    }

    private interface Backend {
        byte[] load(String account);
        void save(String account, byte[] key);
        void delete(String account);
    }

    public interface CoreFoundation extends Library {
        Pointer CFDictionaryCreateMutable(Pointer allocator, NativeLong capacity, Pointer keys, Pointer values);
        void CFDictionarySetValue(Pointer dictionary, Pointer key, Pointer value);
        Pointer CFStringCreateWithCString(Pointer allocator, String text, int encoding);
        Pointer CFDataCreate(Pointer allocator, Pointer bytes, NativeLong length);
        NativeLong CFDataGetLength(Pointer data);
        Pointer CFDataGetBytePtr(Pointer data);
        void CFRelease(Pointer value);
    }

    public interface Security extends Library {
        int SecItemCopyMatching(Pointer query, PointerByReference result);
        int SecItemAdd(Pointer attributes, PointerByReference result);
        int SecItemUpdate(Pointer query, Pointer attributes);
        int SecItemDelete(Pointer query);
    }

    private static final class Mac implements Backend {
        private static final int NOT_FOUND = -25300;
        private final NativeLibrary symbols = NativeLibrary.getInstance("Security");
        private final CoreFoundation core = Native.load("CoreFoundation", CoreFoundation.class);
        private final Security security = Native.load("Security", Security.class);
        private final String namespace;

        Mac(String namespace) { this.namespace = namespace; }

        @Override public byte[] load(String account) {
            try (Query query = query(account)) {
                Pointer yes = NativeLibrary.getInstance("CoreFoundation")
                    .getGlobalVariableAddress("kCFBooleanTrue").getPointer(0);
                query.put("kSecReturnData", yes);
                PointerByReference result = new PointerByReference();
                int status = security.SecItemCopyMatching(query.dictionary, result);
                if (status == NOT_FOUND) return null;
                if (status != 0) throw unavailable();
                Pointer data = result.getValue();
                if (data == null) throw unavailable();
                try {
                    if (core.CFDataGetLength(data).longValue() != KeyMaterial.LENGTH) throw new InvalidSecretException();
                    return core.CFDataGetBytePtr(data).getByteArray(0, KeyMaterial.LENGTH);
                } finally {
                    core.CFRelease(data);
                }
            }
        }

        @Override public void save(String account, byte[] key) {
            try (Query query = query(account); Query attributes = new Query()) {
                Pointer value = attributes.data(key);
                attributes.put("kSecValueData", value);
                int status = security.SecItemUpdate(query.dictionary, attributes.dictionary);
                if (status == NOT_FOUND) {
                    query.put("kSecValueData", value);
                    status = security.SecItemAdd(query.dictionary, null);
                }
                if (status != 0) throw unavailable();
            }
        }

        @Override public void delete(String account) {
            try (Query query = query(account)) {
                int status = security.SecItemDelete(query.dictionary);
                if (status != 0 && status != NOT_FOUND) throw unavailable();
            }
        }

        private Query query(String account) {
            Query query = new Query();
            query.put("kSecClass", symbols.getGlobalVariableAddress("kSecClassGenericPassword").getPointer(0));
            query.string("kSecAttrService", namespace);
            query.string("kSecAttrAccount", account);
            return query;
        }

        private final class Query implements AutoCloseable {
            private final Pointer dictionary = core.CFDictionaryCreateMutable(null, new NativeLong(0), null, null);
            private final List<Pointer> owned = new ArrayList<>();

            Query() { if (dictionary == null) throw unavailable(); }

            void put(String key, Pointer value) {
                core.CFDictionarySetValue(dictionary, symbols.getGlobalVariableAddress(key).getPointer(0), value);
            }

            void string(String key, String text) {
                Pointer value = core.CFStringCreateWithCString(null, text, 0x08000100);
                if (value == null) throw unavailable();
                owned.add(value);
                put(key, value);
            }

            Pointer data(byte[] bytes) {
                try (Memory memory = new Memory(bytes.length)) {
                    try {
                        memory.write(0, bytes, 0, bytes.length);
                        Pointer value = core.CFDataCreate(null, memory, new NativeLong(bytes.length));
                        if (value == null) throw unavailable();
                        owned.add(value);
                        return value;
                    } finally {
                        memory.clear();
                    }
                }
            }

            @Override public void close() {
                core.CFRelease(dictionary);
                for (Pointer value : owned) core.CFRelease(value);
            }
        }
    }

    public interface Credentials extends StdCallLibrary {
        boolean CredReadW(WString target, int type, int flags, PointerByReference result);
        boolean CredWriteW(Credential credential, int flags);
        boolean CredDeleteW(WString target, int type, int flags);
        void CredFree(Pointer credential);
    }

    @Structure.FieldOrder({"flags", "type", "targetName", "comment", "lastWritten", "blobSize", "blob",
        "persist", "attributeCount", "attributes", "targetAlias", "userName"})
    public static final class Credential extends Structure {
        public int flags;
        public int type;
        public WString targetName;
        public WString comment;
        public int[] lastWritten = new int[2];
        public int blobSize;
        public Pointer blob;
        public int persist;
        public int attributeCount;
        public Pointer attributes;
        public WString targetAlias;
        public WString userName;

        public Credential() { super(ALIGN_MSVC); }
        public Credential(Pointer pointer) { super(pointer, ALIGN_MSVC); read(); }
    }

    static final class Windows implements Backend {
        private static final int NOT_FOUND = 1168;
        private final Credentials credentials;
        private final String namespace;

        Windows(String namespace) { this(namespace, Native.load("Advapi32", Credentials.class)); }
        Windows(String namespace, Credentials credentials) { this.namespace = namespace; this.credentials = credentials; }
        private WString target(String account) { return new WString(namespace + "/" + account); }

        @Override public byte[] load(String account) {
            PointerByReference result = new PointerByReference();
            if (!credentials.CredReadW(target(account), 1, 0, result)) {
                if (Native.getLastError() == NOT_FOUND) return null;
                throw unavailable();
            }
            Pointer pointer = result.getValue();
            try {
                Credential credential = new Credential(pointer);
                if (credential.blobSize != KeyMaterial.LENGTH || credential.blob == null) throw new InvalidSecretException();
                return credential.blob.getByteArray(0, credential.blobSize);
            } finally {
                credentials.CredFree(pointer);
            }
        }

        @Override public void save(String account, byte[] key) {
            try (Memory memory = new Memory(key.length)) {
                try {
                    memory.write(0, key, 0, key.length);
                    Credential credential = new Credential();
                    credential.type = 1;
                    credential.targetName = target(account);
                    credential.blobSize = key.length;
                    credential.blob = memory;
                    credential.persist = 2;
                    credential.userName = new WString("CipherChannels");
                    if (!credentials.CredWriteW(credential, 0)) throw unavailable();
                } finally {
                    memory.clear();
                }
            }
        }

        @Override public void delete(String account) {
            if (!credentials.CredDeleteW(target(account), 1, 0) && Native.getLastError() != NOT_FOUND) {
                throw unavailable();
            }
        }
    }

    public interface LibSecret extends Library {
        Pointer secret_schema_new(String name, int flags, Object... attributes);
        void secret_schema_unref(Pointer schema);
        Pointer secret_password_lookup_sync(Pointer schema, Pointer cancellable, PointerByReference error,
                                            Object... attributes);
        int secret_password_store_sync(Pointer schema, String collection, String label, Pointer password,
                                       Pointer cancellable, PointerByReference error, Object... attributes);
        int secret_password_clear_sync(Pointer schema, Pointer cancellable, PointerByReference error,
                                       Object... attributes);
        void secret_password_free(Pointer password);
    }

    public interface GLib extends Library {
        void g_error_free(Pointer error);
    }

    static final class Linux implements Backend {
        private final LibSecret secret;
        private final GLib glib;
        private final String namespace;

        Linux(String namespace) {
            this(namespace, Native.load("secret-1", LibSecret.class), Native.load("glib-2.0", GLib.class));
        }

        Linux(String namespace, LibSecret secret, GLib glib) {
            this.namespace = namespace;
            this.secret = secret;
            this.glib = glib;
        }

        private Pointer schema() {
            Pointer result = secret.secret_schema_new(namespace, 0, "account", 0, null);
            if (result == null) throw unavailable();
            return result;
        }

        private void check(PointerByReference error) {
            if (error.getValue() != null) {
                glib.g_error_free(error.getValue());
                throw unavailable();
            }
        }

        @Override public byte[] load(String account) {
            Pointer schema = schema();
            Pointer password = null;
            try {
                PointerByReference error = new PointerByReference();
                password = secret.secret_password_lookup_sync(schema, null, error, "account", account, null);
                check(error);
                if (password == null) return null;
                // A 32-byte key is exactly 44 Base64 characters, including its padding.
                int length = 0;
                while (length <= 44 && password.getByte(length) != 0) length++;
                if (length != 44) throw new InvalidSecretException();
                byte[] encoded = password.getByteArray(0, 44);
                try {
                    byte[] key;
                    try {
                        key = Base64.getDecoder().decode(encoded);
                    } catch (IllegalArgumentException exception) {
                        throw new InvalidSecretException();
                    }
                    if (key.length != KeyMaterial.LENGTH) {
                        Arrays.fill(key, (byte) 0);
                        throw new InvalidSecretException();
                    }
                    return key;
                } finally {
                    Arrays.fill(encoded, (byte) 0);
                }
            } finally {
                if (password != null) secret.secret_password_free(password);
                secret.secret_schema_unref(schema);
            }
        }

        @Override public void save(String account, byte[] key) {
            Pointer schema = schema();
            byte[] encoded = Base64.getEncoder().encode(key);
            try (Memory password = new Memory(encoded.length + 1L)) {
                try {
                    password.clear();
                    password.write(0, encoded, 0, encoded.length);
                    PointerByReference error = new PointerByReference();
                    int stored = secret.secret_password_store_sync(schema, null, "CipherChannels",
                        password, null, error, "account", account, null);
                    check(error);
                    if (stored == 0) throw unavailable();
                } finally {
                    password.clear();
                }
            } finally {
                Arrays.fill(encoded, (byte) 0);
                secret.secret_schema_unref(schema);
            }
        }

        @Override public void delete(String account) {
            Pointer schema = schema();
            try {
                PointerByReference error = new PointerByReference();
                secret.secret_password_clear_sync(schema, null, error, "account", account, null);
                check(error);
                byte[] remaining = load(account);
                if (remaining != null) {
                    Arrays.fill(remaining, (byte) 0);
                    throw unavailable();
                }
            } finally {
                secret.secret_schema_unref(schema);
            }
        }
    }
}
