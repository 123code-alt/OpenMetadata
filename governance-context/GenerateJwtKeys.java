import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;

/** Run with JDK 21: java governance-context/GenerateJwtKeys.java conf/local-keys */
class GenerateJwtKeys {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Supply a new output directory");
    }
    Path directory = Path.of(args[0]);
    Files.createDirectory(directory); // Deliberately refuses to overwrite existing keys.
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    Files.write(directory.resolve("private_key.der"), pair.getPrivate().getEncoded());
    Files.write(directory.resolve("public_key.der"), pair.getPublic().getEncoded());
    System.out.println("Created PKCS#8 private key and X.509 public key in " + directory);
  }
}
