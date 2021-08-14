package tk.elian.ezpaneldaemon.util;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

// modified from https://github.com/mirraj2/bowser/blob/41ca8b9512c62e25f231dd47084ac60b67a5b44c/src/bowser/misc/SSLUtils.java
public class SSLUtil {

	public static SSLContext createContext(String directory, String pass, String country, String state,
	                                       String locality, String organization, String email) {
		File dir = new File(directory);
		if (!dir.exists()) {
			dir.mkdir();
		}

		File keystoreFile = new File(dir, "keystore.jks");
		File pemFile = new File(dir, "cacert.pem");

		boolean generateKeystore = false;

		if (keystoreFile.exists()) {
			if (keystoreFile.lastModified() < pemFile.lastModified()) {
				System.out.println("FTP SSL: It looks like a new PEM file was created. Regenerating the keystore.");
				keystoreFile.delete();
				generateKeystore = true;
			}
		} else {
			generateKeystore = true;
		}

		if (generateKeystore) {
			try {
				String command = String.format("openssl req -newkey rsa:2048 -x509 -keyout cakey.pem -out cacert.pem" +
								" -days 3650 -passout pass:%s -subj /C=%s/ST=%s/L=%s/O=%s/OU=%s/CN=%s",
						pass, country, state, locality, organization, organization, email);
				new ProcessBuilder(command.split(" ")).directory(dir).start().waitFor();

				command = String.format("openssl pkcs12 -export -in cacert.pem -inkey cakey.pem -out identity.p12 " +
						"-name \"ezPanel_Key\" -passin pass:%s -passout pass:%s", pass, pass);
				new ProcessBuilder(command.split(" ")).directory(dir).start().waitFor();

				command = String.format("keytool -importkeystore -destkeystore keystore.jks -deststorepass %s " +
						"-srckeystore identity.p12 -srcstoretype PKCS12 -srcstorepass %s", pass, pass);
				new ProcessBuilder(command.split(" ")).directory(dir).start().waitFor();

				new File(dir, "identity.p12").delete();// cleanup
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			keystore.load(new FileInputStream(keystoreFile), pass.toCharArray());

			KeyManagerFactory keyManagerFactory =
					KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keystore, pass.toCharArray());

			SSLContext ret = SSLContext.getInstance("TLSv1.2");
			TrustManagerFactory factory = TrustManagerFactory.getInstance(
					TrustManagerFactory.getDefaultAlgorithm());
			factory.init(keystore);
			ret.init(keyManagerFactory.getKeyManagers(), factory.getTrustManagers(), null);

			return ret;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
