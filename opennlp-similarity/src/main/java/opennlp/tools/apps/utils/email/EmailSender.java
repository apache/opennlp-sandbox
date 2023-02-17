/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.apps.utils.email;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsible to sending e-mails through a gmail smtp server.
 * It will be extended to handle arbitrary smtp servers.
 *
 * @author GaDo
 */
public class EmailSender {
	private static final Pattern pc = Pattern.compile("[^\\s]+@[^\\s]+.[^\\s]+");

	private static final String mailboxAddress = "boris_galitsky@rambler.ru";

	public boolean sendMail(String smtp, String user, String pass,
													 InternetAddress from, InternetAddress[] to, InternetAddress[] cc, InternetAddress[] bcc,
													 String subject, String body, String file) throws Exception {

		boolean correct;
		try {
			// Eliminate spaces from addresses
			if(from!=null){
				from.setAddress(from.getAddress().replace(" ","").trim());
			}
			to = eliminateSpaces(to);
			cc = eliminateSpaces(cc);
			bcc = eliminateSpaces(bcc);
			correct = validateAddress(from,to,cc,bcc);

			if(correct){
				// Configuration of the properties -> smtp
				Properties props = new Properties();
				props.put("mail.smtp.host", smtp);
				props.put("mail.smtp.auth", "true");
				props.put("mail.smtp.port", "465");
				props.put("mail.smtp.starttls.enable", "true");
				Authenticator auth = new SMTPAuthenticator(user, pass);
				Session session = Session.getInstance(props, auth);
				//Session session = Session.getDefaultInstance(props);
				//props.put("mail.smtp.user",user);
				//props.put("mail.smtp.password",pass);

				// Composing the message
				MimeMessage message = new MimeMessage(session);
				message.setFrom(from);
				message.setRecipients(Message.RecipientType.TO,to);
				message.setRecipients(Message.RecipientType.CC,cc);
				message.setRecipients(Message.RecipientType.BCC,bcc);
				message.setSubject(subject);
				if(file==null) {
					//message.setText(body);
					message.setContent(body, "text/html");
				}
				else {
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
		catch(Exception e) {
			e.printStackTrace();
			correct=false;
		}
		return correct;
	}

	private  boolean validateAddress(InternetAddress from,
			InternetAddress[] to, InternetAddress[] cc, InternetAddress[] bcc) {

		boolean correct;
		try {
			correct = from!=null && !from.getAddress().equals("") && to!=null && to.length>=1;

			Matcher m;

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

		} catch(Exception e){
			e.printStackTrace();
			correct=false;
		}
		return correct;
	}

	private  InternetAddress[] eliminateSpaces(InternetAddress[] address) {
		if(address!=null){
			for (InternetAddress internetAddress : address) {
				internetAddress.setAddress(internetAddress.getAddress().replace(" ", "").trim());
			}
		}
		return address;
	}
		
}
