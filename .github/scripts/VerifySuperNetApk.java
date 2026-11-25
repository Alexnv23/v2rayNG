import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class VerifySuperNetApk {
    private static final String RELEASE_CERTIFICATE =
            "2c882b78ec03386963b9cc0f20a75e4528551642ee9b6f464c7ad8cccd60dbe6";

    public static void main(String[] args) throws Exception {
        File apk = new File(args[0]);
        var result = new ApkVerifier.Builder(apk).build().verify();
        if (!result.isVerified()) {
            fail("Android signature verification failed: " + result.getAllErrors());
        }
        var signers = result.getSignerCertificates();
        if (signers.size() != 1) {
            fail("Expected one verified APK signer, found " + signers.size());
        }
        byte[] certificate = signers.get(0).getEncoded();
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate));
        if (!RELEASE_CERTIFICATE.equals(fingerprint)) {
            fail("Unexpected APK signer: " + fingerprint);
        }
        try (ZipFile archive = new ZipFile(apk)) {
            for (String abi : List.of("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
                for (String library : List.of("libgojni.so", "libhev-socks5-tunnel.so", "libhevsockstun.so")) {
                    String path = "lib/" + abi + "/" + library;
                    ZipEntry entry = archive.getEntry(path);
                    if (entry == null || entry.getSize() <= 0) {
                        fail("Missing or empty native library: " + path);
                    }
                }
            }
        }
        System.out.println("Verified SuperNet certificate " + fingerprint + " and all four ABIs.");
    }

    private static void fail(String message) {
        System.err.println("::error::" + message);
        throw new IllegalStateException(message);
    }
}
