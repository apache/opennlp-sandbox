package opennlp.tools.apps.utils.email;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.*;
import javax.mail.internet.*;
import javax.activation.*;
/**
 * Responsible to sending e-mails trough a gmail smtp server.
 * It will be extended to handle arbitrary smtp servers.
 * @author GaDo
 *
 */
public class EmailSender {
		private static final long serialVersionUID = 1L;
		private static final String mailboxAddress="boris_galitsky@rambler.ru";

		public  boolean sendMail(String smtp, String user, String pass, InternetAddress from, InternetAddress[] to, InternetAddress[] cc, InternetAddress[] bcc, String subject, String body, String file) throws Exception
		{
			boolean correct=true;
			try
			{							
				//Eliminate spaces from addresses
				if(from!=null){		
					from.setAddress(from.getAddress().replace(" ","").trim());		}
					to = eliminateSpaces(to);
					cc = eliminateSpaces(cc);
					bcc = eliminateSpaces(bcc);
					correct = validateAddress(from,to,cc,bcc);
				
				if(correct){
					//Configuracio of the properties -> smtp
					Properties props = new Properties();
					props.put("mail.smtp.host", smtp);
					props.put("mail.smtp.auth", "true");
					props.put("mail.smtp.port", "465");
					props.put("mail.smtp.starttls.enable", "true");
					Authenticator auth = new SMTP_Authenticator	(user, pass);
					Session session = Session.getInstance(props, auth);
					//Session session = Session.getDefaultInstance(props);
					//props.put("mail.smtp.user",user);
					//props.put("mail.smtp.password",pass);
												    
				    //Composing the message
				    MimeMessage message = new MimeMessage(session);
				      message.setFrom(from);
				    message.setRecipients(Message.RecipientType.TO,to);
				    message.setRecipients(Message.RecipientType.CC,cc);
				    message.setRecipients(Message.RecipientType.BCC,bcc);
				    message.setSubject(subject);
				    if(file==null)
				    {
				    	
					    //message.setText(body);
				    	message.setContent(body, "text/html");
				    }
				    else
				    {
					    Multipart multipart = new MimeMultipart();
					    BodyPart messageBodyPart;
					    messageBodyPart = new MimeBodyPart();
					    messageBodyPart.setContent(body, "text/html");
					    //messageBodyPart.setText(body);
					    multipart.addBodyPart(messageBodyPart);
					    messageBodyPart = new MimeBodyPart();
					    DataSource source = new FileDataSource(file);
					    messageBodyPart.setDataHandler(new DataHandler(source));
					    messageBodyPart.setFileName(file);
					    multipart.addBodyPart(messageBodyPart);
		
					    message.setContent(multipart);
				    }
		
					Transport tr = session.getTransport("smtp");			
					tr.connect(smtp, mailboxAddress, pass);
					message.saveChanges();
					tr.sendMessage(message, message.getAllRecipients());
					tr.close();
				}
		    }
			catch(Exception e)
			{
				e.printStackTrace();
				correct=false;
			}
			return correct;
		}

		private  boolean validateAddress(InternetAddress from,
				InternetAddress[] to, InternetAddress[] cc,
				InternetAddress[] bcc) {
			boolean correct = true;
			try{
				correct = from!=null && !from.getAddress().equals("") && to!=null && to.length>=1;
				String regEx="[^\\s]+@[^\\s]+.[^\\s]+";
				Pattern pc = Pattern.compile(regEx);
				Matcher m = null ;

				if(correct){
					m = pc.matcher(from.getAddress());
					correct = m.matches();
				}
				
				if(correct){
					int vault = to.length;
					while(correct && vault<to.length){
						correct = !to[vault].getAddress().equals("");
						if(correct){
					    	m = pc.matcher(to[vault].getAddress());
					    	correct = m.matches();
						}
						vault++;
					}
				}
				
				if(correct && cc!=null){
					int vault = cc.length;
					while(correct && vault<cc.length){
						correct = !cc[vault].getAddress().equals("");
						if(correct){
					    	m = pc.matcher(cc[vault].getAddress());
					    	correct = m.matches();
						}
						vault++;
					}
				}
				
				if(correct && bcc!=null){
					int vault = bcc.length;
					while(correct && vault<bcc.length){
						correct = !bcc[vault].getAddress().equals("");
						if(correct){
					    	m = pc.matcher(bcc[vault].getAddress());
					    	correct = m.matches();
						}
						vault++;
					}
				}
				
			}catch(Exception e){
				e.printStackTrace();
				correct=false;
			}
			return correct;
		}

		private  InternetAddress[] eliminateSpaces(InternetAddress[] address) {
			if(address!=null){
				for(int i=0;i<address.length;i++){
					address[i].setAddress(address[i].getAddress().replace(" ","").trim());
				}
			}
			return address;
		}		

		
		public static void main(String[] args){
			EmailSender s = new EmailSender();
			try {
				s.sendMail("smtp.rambler.ru", "boris_galitsky@rambler.ru", "b06g93", 
						new InternetAddress("bgalitsky@hotmail.com"), new InternetAddress[]{new InternetAddress("bgalitsky@hotmail.com")}, new InternetAddress[]{}, new InternetAddress[]{}, 
						"Generated content for you", "body", null);
			} catch (AddressException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
}
