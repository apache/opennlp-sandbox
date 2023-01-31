package opennlp.tools.apps.utils.email;
import javax.mail.*;


/**
 * This contains the required informations for the smtp authorization!
 *
 */

public class SMTP_Authenticator extends javax.mail.Authenticator {
	
	private String username="bg7550@gmail.com";
	private String password="pill0693";	
	
	public SMTP_Authenticator(String user, String pwd) {
		username=user;
		password=pwd;
	}

		
	public PasswordAuthentication getPasswordAuthentication() {
		return new PasswordAuthentication(username, password);
		}
}
