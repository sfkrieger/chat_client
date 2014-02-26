/*
 * University of British Columbia
 * Department of Computer Science
 * CPSC317 - Internet Programming
 * Assignment 1
 * 
 * Author: 
 * January 2012
 * 
 * This code may not be used without written consent of the authors, except for 
 * current and future projects and assignments of the CPSC317 course at UBC.
 */

package ubc.cs317.xmpp.net;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collection;

import javax.xml.bind.DatatypeConverter;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


import ubc.cs317.xmpp.exception.XMPPException;
import ubc.cs317.xmpp.model.Contact;
import ubc.cs317.xmpp.model.ContactStatus;
import ubc.cs317.xmpp.model.Conversation;
import ubc.cs317.xmpp.model.Message;
import ubc.cs317.xmpp.model.Session;

/**
 * This class describes the XMPP connection handler. A socket connection is
 * created when an instance of this handler is created, and methods are provided
 * for most common operations in XMPP.
 * 
 * This class will not in any case make a direct reference to any class or
 * method that represents a specific UI library.
 */
public class XMPPConnection {

	/**
	 * Default TCP port for client-server communication in XMPP.
	 */
	public static final int XMPP_DEFAULT_PORT = 5222;

	/**
	 * Session object for communication between the network component and the
	 * chat model and UI.
	 */
	private Session session;

	/**
	 * Socket object associated to the communication between this client and the
	 * XMPP server.
	 */
	private Socket socket;

	/**
	 * XMPP reader helper, used to obtain XML nodes from the XMPP stream.
	 */
	private XMPPStreamReader xmppReader;

	/**
	 * XMPP writer helper, used to write XML nodes to the XMPP stream.
	 */
	private XMPPStreamWriter xmppWriter;
	
	/**
	 * S:Authentications element
	 */
	private Element features;
	
	/**
	 * bad idea: jidUser
	 */
	private String userId;

	/**
	 * bad idea: jidDomain
	 */
	private String domain;
	
	/*
	 * global variable for maintaining unique ids
	 */
	private static int incid = 0;
	
	/*
	 * error type, i don't know if it realy needs to be a static final but why not
	 */
	private static final String ERROR = "error";
	/**
	 * Creates a new instance of the connection handler. This constructor will
	 * creating the socket, initialise the reader and writer helpers, send
	 * initial tags, authenticate the user and bind to a resource.
	 * 
	 * @param jidUser
	 *            User part of the Jabber ID.
	 * @param jidDomain
	 *            Domain part of the Jabber ID.
	 * @param resource
	 *            Resource to bind once authenticated. If null or empty, a new
	 *            resource will be generated.
	 * @param password
	 *            Password for authentication.
	 * @param session
	 *            Instance of the session to communicate with other parts of the
	 *            system.
	 * @throws XMPPException
	 *             If there is an error establishing the connection, sending or
	 *             receiving necessary data, or while authenticating.
	 */
	public XMPPConnection(String jidUser, String jidDomain, String resource,
			String password, Session session) throws XMPPException {
		
		this.session = session;

		initializeConnection(jidDomain);

		try {
			xmppReader = new XMPPStreamReader(socket.getInputStream());
			xmppWriter = new XMPPStreamWriter(socket.getOutputStream());
		} catch (XMPPException e) {
			throw e;
		} catch (Exception e) {
			throw new XMPPException("Could not obtain socket I/O channels ("
					+ e.getMessage() + ")", e);
		}

		initializeStreamAndFeatures(jidUser, jidDomain);

		login(jidUser, password);

		bindResource(resource);

		startListeningThread();
	}

	/**
	 * Initialises the connection with the specified domain. This method sets
	 * the socket field with an initialised socket.
	 * 
	 * @param domain
	 *            DNS name (or IP string) of the server to connect to.
	 * @throws XMPPException
	 *             If there is a problem connecting to the server.
	 */
	private void initializeConnection(String domain) throws XMPPException {
		/* YOUR CODE HERE */

		try {
			socket = new Socket(domain, XMPP_DEFAULT_PORT);
		} catch (UnknownHostException e) {
			throw new XMPPException("Unkown host");
		} catch (IOException e) {
			throw new XMPPException("Coudn't get I/O for the connection to: " + domain);

		}
	}

	/**
	 * Sends the initial data to establish an XMPP connection stream with the
	 * XMPP server. This method also retrieves the set of features from the
	 * server, saving it in a field for future use.
	 * 
	 * @param jidUser
	 *            User part of the Jabber ID.
	 * @param jidDomain
	 *            Domain part of the Jabber ID.
	 * @throws XMPPException
	 *             If there is a problem sending or receiving the data.
	 */
	private void initializeStreamAndFeatures(String jidUser, String jidDomain)
			throws XMPPException {

		/* YOUR CODE HERE */
		
		  // started a stream...
		  // 1. how to view whats being put in the reader? do i have to parse
		  // out the output from the reader... to see what it returns
		  //2. where does this 'feature' field' come from, presumably i parse it later on
		  // do i need to worry about (4.3.1 END)?
		  //where is this response located?
		userId = jidUser;
		domain = jidDomain;
		Element root = xmppWriter.createRootElement("stream:stream");
		root.setAttribute("from", userId);
		  root.setAttribute("to", domain);
		  root.setAttribute("version", "1.0");
		  root.setAttribute("xml:lang", "en");
		  root.setAttribute("xmlns", "jabber:client");
		  root.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
		  xmppWriter.debugElement(System.out, root);		

		  xmppWriter.writeRootElementWithoutClosingTag();
		  features = xmppReader.readSecondLevelElement();
		  xmppWriter.debugElement(System.out, features);
	}
	
	private boolean presentsPLAINMech(){
		NodeList authList = features.getElementsByTagName("mechanism");
		for(int i = 0 ; i < authList.getLength(); i++){
			Node item = authList.item(i).getFirstChild();
//			String nameSpace = item.getNamespaceURI();
//			String localName = item.getLocalName();
//			String text = item.getTextContent();
//			String name = item.getNodeName();
			String nodeName = item.getNodeValue();
			if(nodeName.equals("PLAIN"))
				return true;
		}
		return false;
	}

	/**
	 * Attempts to authenticate the user name with the provided password at the
	 * connected server. This method will verify if the server supports the
	 * implemented authentication mechanism(s) and send the user and password
	 * based on the first mechanism it finds. In case authentication is not
	 * successful, this function will close the connection and throw an
	 * XMPPException. This function also retrieves the new set of features
	 * available after authentication.
	 * 
	 * @param username
	 *            User name to use for authentication.
	 * @param password
	 *            Password to use for authentication.
	 * @throws XMPPException
	 *             If authentication is not successful, or if authentication
	 *             methods supported by the server are not implemented, or if
	 *             there was a problem sending authentication data.
	 */
	private void login(String username, String password) throws XMPPException {
		/* YOUR CODE HERE */
		if(!presentsPLAINMech()){
			this.closeConnection();
			throw new XMPPException("Can not authenticate with this server");
		}
		byte[] data = ("\u0000" + username + "\u0000" + password).getBytes();
		String content = DatatypeConverter.printBase64Binary(data);
		Element init = xmppWriter.createElement("auth");
		init.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
		init.setAttribute("mechanism", "PLAIN");
		init.setTextContent(content);
		xmppWriter.writeIndividualElement(init);
		System.out.println("\n" + "Login: ");
		xmppWriter.debugElement(System.out, init);
		//1. If the initiating entity subsequently sends another <auth/> element 
		//and the ongoing authentication handshake has not yet completed, the 
		//receiving entity MUST discard the ongoing handshake and MUST process a new 
		//handshake 
		//for the subsequently requested SASL mechanism.
		//must restart the stream when completed...
		 Element response = xmppReader.readSecondLevelElement();
		 String nodeName = response.getNodeName();
		 System.out.println("\n" + "Response from server: ");
		  xmppWriter.debugElement(System.out, response);

		 if(response.getNodeName().equals("failure")){
			 NodeList failures = response.getChildNodes();
			 String reason = failures.item(0).getNodeName();
			 throw new XMPPException("Login Failure, this might indicate the reason: " + reason);
		 }else if(response.getNodeName().equals("success")){
			 	xmppWriter.writeRootElementWithoutClosingTag();
				  System.out.println("\n" + "If you've gotten a success, this will be what is written out:");
//				  xmppWriter.debugElement(System.out, root);			 
				  Element response2 = xmppReader.readSecondLevelElement();
				  System.out.println("\n" + "And this is what you'll get as a response: ");
				  xmppWriter.debugElement(System.out, response2);
		 }
		
	}

	/**
	 * Binds the connection to a specific resource, or retrieves a
	 * server-generated resource if one is not provided. This function will wait
	 * until a resource is sent by the server.
	 * 
	 * @param resource
	 *            Name of the user-specified resource. If resource is null or
	 *            empty, retrieves a server-generated one.
	 * @throws XMPPException
	 *             If there is an error sending or receiving the data.
	 */
	private void bindResource(String resource) throws XMPPException {
		/* YOUR CODE HERE */
		Element iq = xmppWriter.createElement("iq");
		iq.setAttribute("type", "set");
		Element bind = xmppWriter.createElement("bind");
		bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");

		//try to make a helper function that has the id as being unique
		if((resource == null) || resource.equals("")){
			//server can generate the resource
			iq.setAttribute("id", getUniqueIdValue());
			iq.appendChild(bind);			
			
		}else{
			iq.setAttribute("id", getUniqueIdValue());
			Element resorc = xmppWriter.createElement("resource");
			resorc.setTextContent(resource);
			bind.appendChild(resorc);
			iq.appendChild(bind);	
		}
		//debug it
		System.out.println("\n" + "Where going to write to server, when binding resource: ");
		xmppWriter.debugElement(System.out, iq);
		
		//now write it out
		xmppWriter.writeIndividualElement(iq);
		
		//response from server with jid
		Element response = xmppReader.readSecondLevelElement();
		System.out.println("\n" + "Response from server in binding resource: ");
		xmppWriter.debugElement(System.out, response);
		
		//check if it is of type result...otherwise its an error
		String respType = response.getAttribute("type");
		if(respType.equals("error")){
			//get the error type
			//short errorType = response.getElementsByTagName("error").item(0).
			throw new XMPPException("Could not bind requested resource. Error type: ");
		}else{
			NodeList res = response.getElementsByTagName("jid");
			//assume what is returned is only 1 element
			String jid = res.item(0).getTextContent();
			
			//probably should do something with this...this is the resource name... if anyone wants it
			session.setUserJid(jid);
			System.out.println("\n" + "JID saved to user's ID: " + session.getUserJid());
			
		}
		
		
	}

	/**
	 * Starts a thread that will keep listening for new messages asynchronously
	 * from the main thread.
	 */
	private void startListeningThread() {
		Thread listeningThread = new Thread(new Runnable() {

			@Override
			public void run() {
				listeningProcess();
			}
		});
		listeningThread.start();
	}

	/**
	 * Keeps listening for new XML elements in a loop until a closing tag is
	 * found or an exception happens. If an exception happens, calls
	 * <code>session.processReceivedException</code> and closes the connection.
	 * If the closing tag is found, sends a closing tag back and closes the
	 * connection as well. In both cases, the connection is closed at the
	 * session level, so that the model and UI can handle the connection being
	 * closed. For each received element, processes the element and handles it
	 * accordingly.
	 */
	private void listeningProcess() {
		Exception ex = new Exception();
		/* YOUR CODE HERE */
		//loop that waits for messages form the server
		//when message is reieved, handle the XML element according to RFC
		//any message that isn't supported can be ignored
		
		//interrupt the loop if:
			//1. connection closed (i.e. 4.4 of 6120)
			//2. or exception thrown waitinf for new messages
				//if 2. happens then session.processRecievedException should be called
				//and you want to close the connection :close connection on stream class
		while(!xmppReader.isDocumentComplete()){
			//catch exception if its thrown : if()
			//

			try {
				Element toProcess = xmppReader.readSecondLevelElement();
				String endTag = toProcess.getTagName();
				if(endTag.equals("stream:stream")){
					//but if you call close connecion on session then it will try to close the connection again...
					//xmppWriter.writeCloseTagRootElement();
					
					//should call close connection
					session.closeConnection();
					break;
				}else{
					this.processElement(toProcess);
				}
			} catch (XMPPException e) {
				session.processReceivedException(e);
				session.closeConnection();
				break;
				
			}
			
		}
	}

	private void processElement(Element toProcess) {
		//process the element
		//element you're about to process:
		System.out.println("\n" + "Element you're about to process: ");
		xmppWriter.debugElement(System.out, toProcess);
		String tagName = toProcess.getTagName().toLowerCase();
		System.out.println("\n" + "Tag name: " + tagName);
		if(tagName.equals("iq")){
			this.processIQ(toProcess);
		}else if(tagName.equals("presence")){
			this.processPresence(toProcess);
		}else if(tagName.equals("message")){
			this.processMessage(toProcess);
		}else if(tagName.equals("ping")){
			//TODO send a pong
		}
		
	}

	private void processMessage(Element toProcess) {
		//TODO finish processing the message types
		
		String type = toProcess.getAttribute("type").toLowerCase();
		if(type.equals("chat")){
			//do stuff
			String fromContact = toProcess.getAttribute("from");
			String toFullJID = toProcess.getAttribute("to");
			Contact fcontact = session.getContact(fromContact);
			
			//if you don't have the contact then i don't know what you shold do
			if(fcontact == null){
				System.out.println("You're getting a message from a contact that isn't in your contact list"
						+ "I'm deciding not to support that behavior because its unclear whether or not i need to");
				System.out.println("\n" + "Reminder: I did allow OUTGOING messages to contacts that are unlisted, (meaning myself)");
			}else{
				Conversation convo = session.getConversation(fcontact);
				String[] fullJID = toFullJID.split("/");
				
				//assume it has provided the rescource, or else you're in trouble
				//i'm not sure if i should really create a contact with the to field being myself....
				//presumably the to field should be null because presumably you're the reciever...
				String resource = fullJID[1];
				NodeList messages = toProcess.getElementsByTagName("body");

				//assume its the first one you care about..
				Element mess = (Element) messages.item(0);
				String content = mess.getTextContent();
				Message recievedMess = new Message(fcontact, null, content);
				convo.addIncomingMessage(recievedMess, resource);
			}
		}
	}

	private void processPresence(Element toProcess) {
		String contact = toProcess.getAttribute("from");
		String[] fullJID = contact.split("/");
		Contact theContact = session.getContact(contact);
		
		if(toProcess.getAttribute("type").equalsIgnoreCase("subscribe")){
			session.handleReceivedSubscriptionRequest(fullJID[0]);
		}
		
		//probably shouldn't do anything if it can't be found 
		if(theContact != null){ //then you have the contact
			String resource = null;
			if(fullJID.length > 1){
				//then you can assume i guess that there is a resource.  
				//i really don;t know...
				resource = fullJID[1];
			}else{
				System.out.println("\n" + "it looks like there is no resource... "
			+ "so lets hope you're getting a request for subscription and not" +
						"setting a status to a contact that has no resources...");
			}
			
			//CHECK FOR UNAVAILABLE
			//check if they're unavailable
			String hasType = toProcess.getAttribute("type");
			if( hasType != null){
				if(hasType.toLowerCase().equals("unavailable")){
					//everything is formated correctly
					theContact.setStatus(resource, ContactStatus.OFFLINE);
				}else if(hasType.toLowerCase().equals("unsubscribe")){
					session.removeContact(theContact);
				}
			}else{// THEN THERE IS NO TYPE
				//THREE KINDS OF ONINE BEHAVIOUR 
				//then it should be online...
			
				NodeList showTags = toProcess.getElementsByTagName("show");
				
				//if it is something other than just general 'online'...:
				if(showTags.getLength() != 0){

					//take the first one, doesn't make sense if theres more than one i think
					Element showElem = (Element) showTags.item(0);
					String showText = showElem.getTextContent().toLowerCase();
					//ContactStatus.getContactStatus(showText)
					if(showText.equalsIgnoreCase("away")){
						theContact.setStatus(resource, ContactStatus.AWAY);
					}else if(showText.equalsIgnoreCase("chat")){
						theContact.setStatus(resource, ContactStatus.CHAT);
					}else if(showText.equalsIgnoreCase("dnd")){
						theContact.setStatus(resource, ContactStatus.DND);
					}else if(showText.equalsIgnoreCase("xa")){
						theContact.setStatus(resource, ContactStatus.XA);
					}
					//maybe send a 'bad format' error back to the server... because you shouldn't have show elements
					//containing anything other than the list above
					System.out.println("\n" + "Either you're doiing something wrong, or you've gotten a show element thats outisde of " + 
					"what servers should send as show element contents");
				}else{
					// then it is a contact that is online but doesn't present a show element... so update it to online
					theContact.setStatus(resource, ContactStatus.AVAILABLE);
				}//closing conditions for length of showTag list
			}//Closing the bigger case of it being not unavailable but being in your contact list (dealing with the actual processing.. 
			}
		else{
			
			System.out.println("\n" + "It looks like you've gotten some sort of presence from a contact that isn't in your " +
					"roster. that is probably a problem. with your code. Or its you getting youre own presence.");
		}

	}

	private void processIQ(Element toProcess) {
		//at this point, this is trivial and should be taken out...
		
		String iqType = toProcess.getAttribute("type"); 
		//assuming it has an element child because all iq elements seem to at least have one child
		Element iqChild = (Element) toProcess.getFirstChild();

		if(iqType.equalsIgnoreCase("result")){
			if(iqChild != null){
				//maybe don't include the if statement?
				if(iqChild.getTagName().equalsIgnoreCase("query")){
					System.out.println("This is your list of contacts, both acepted and not accepted");
					this.addContacts(toProcess, true);
				}
			}else{
				
			//if it is null then you probably succesfully added a contact
				System.out.println("\n" + "Hopefully this means you successfully added a contact.");
			}	
		}else if(iqType.equalsIgnoreCase(ERROR)){
			//then the child of the iq should have its own child which is the error specific info
			//i.e. two children in... this is a leap in logic as it assumes that all errors have two nested children
			//but this whole thing is rife with assumptions so why not add another one
			Element itemNotFound = (Element) iqChild.getFirstChild();
			//this this should have a nested item not found element
			System.out.println("Attempted to request a roster that doesn't exist: Error of type" 
			+ iqChild.getAttribute("type") + " and " + itemNotFound.getLocalName() + " description");
		}else if(iqType.equalsIgnoreCase("set")){
			this.addContacts(toProcess, false);
		}else if(iqType.equalsIgnoreCase("get")){
			
		}
			
	}

	private void addContacts(Element toProcess, boolean isARosterResult) {
		NodeList contacts = toProcess.getElementsByTagName("item");
		for(int i = 0; i < contacts.getLength(); i++){
			Element elem = (Element) contacts.item(i);
			String subscription = elem.getAttribute("subscription");
			String ask = elem.getAttribute("ask");
			if((subscription != null)){
				String user = elem.getAttribute("jid");
				Contact nullIfNew = session.getContact(user);
//				if(ask.equalsIgnoreCase("subscribe")){
//					//session.
//					session.handleReceivedSubscriptionRequest(user);
//					//TODO: figure out if you need to add the contact now, or later
//				}
				if(subscription.equalsIgnoreCase("both") || subscription.equalsIgnoreCase("to") || ask.equalsIgnoreCase("ask")){
					String alias = elem.getAttribute("name");
					//the following would be more efficient. but i have a feeling you can't do it like this
					if(nullIfNew == null){
						session.addReceivedContact(new Contact(user, alias));
					}
				}else if(subscription.equalsIgnoreCase("remove")){
					if(nullIfNew == null){
						session.removeContact(nullIfNew);
					}
				}else if(ask.equalsIgnoreCase("subscribe") && !isARosterResult){
					//then you got a request foa contact i think
				}
				
			}
			
		}
		
	}

	/**
	 * Closes the connection. If the connection was already closed before this
	 * method is called nothing is done, otherwise sends all necessary closing
	 * data and waits for the server to send the closing data as well. Once this
	 * happens the socket connection is closed. This method does not throw any
	 * exception, choosing instead to ignore them. However, even if an exception
	 * happens while sending the final data, the socket connection will be
	 * closed.
	 */
	public synchronized void closeConnection() {
		/*
		 * This method might, for some reason, be called more than once in the same connection. 
		 * In that case, you should 
		 * check if the connection has already been closed and, if so, return without taking any action.
		 */
		/* YOUR CODE HERE */
		if(!socket.isClosed()){
			Element presence = null;
			//i think you only send a presence if the document is INCOMPLETE
			if(!xmppReader.isDocumentComplete()){
				presence = xmppWriter.createElement("presence");
				presence.setAttribute("type", "unavailable");
				Element status = xmppWriter.createElement("status");
				status.setTextContent("leaving");
				presence.appendChild(status);
			}
			
			//now actually close the stream
			try {
				if(presence != null) 
					xmppWriter.writeIndividualElement(presence);
				xmppWriter.writeCloseTagRootElement();
				System.out.println();
				System.out.println("Closing socket connection: debugging");
				xmppWriter.debugElement(System.out, presence);
				xmppReader.waitForCloseDocument();
				System.out.println("Is doc complete? " + xmppReader.isDocumentComplete());
			} catch (XMPPException e) {
				e.printStackTrace();
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		
	}

	/**
	 * Sends a request for the contact list. The result is not expected to be
	 * received in this function, but it should come in a message that will be
	 * handled by the listening process.
	 * 
	 * @throws XMPPException
	 *             If there was a problem sending the request.
	 */
	public void sendRequestForContactList() throws XMPPException {
		//doesn't need to actually retrieve the list, just requests the list
		/* YOUR CODE HERE */
		
		/*
		 * <iq from='juliet@example.com/balcony' id='bv1bs71f' type='get'>
    			<query xmlns='jabber:iq:roster'/>
  			</iq>
		 */
		Element contactLR = xmppWriter.createElement("iq");
		contactLR.setAttribute("from", session.getUserJid());
		contactLR.setAttribute("id", this.getUniqueIdValue());
		contactLR.setAttribute("type", "get");
		Element query = xmppWriter.createElement("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		contactLR.appendChild(query);
		xmppWriter.writeIndividualElement(contactLR);
		System.out.println("\n" + "Sending request for contact list: ");
		xmppWriter.debugElement(System.out, contactLR);
	}

	private String getUniqueIdValue() {
		return "sammy" + incid++;
	}

	/**
	 * Sends an updated status information to the server, based on the status
	 * currently attributed to the session.
	 * 
	 * @throws XMPPException
	 *             If there was a problem sending the status.
	 */
	public void sendCurrentStatus() throws XMPPException {
		sendStatus(session.getCurrentStatus());
	}

	/**
	 * Sends a specific status information to the server.
	 * 
	 * @param status
	 *            Status to send to the server.
	 * @throws XMPPException
	 *             If there was a problem sending the status.
	 */
	private void sendStatus(ContactStatus status) throws XMPPException {
		Element presence = xmppWriter.createElement("presence");
		if(status.isOnline()){
			//then you know they're online...
			String showString = status.getXmppShow();
			if(showString != null){
				Element showElem = xmppWriter.createElement("show");
				showElem.setTextContent(showString);
				presence.appendChild(showElem);
			}
		}else{
			presence.setAttribute("type", "unavailable");
		}
		
		String userMes = status.getUserFriendlyName();
		if(userMes != null){
			Element userContent = xmppWriter.createElement("status");
			userContent.setTextContent(userMes);
			presence.appendChild(userContent);
		}
		xmppWriter.writeIndividualElement(presence);
		System.out.println("\n" + "And here is the presence that was sent (hopefully): " + presence);
		xmppWriter.debugElement(System.out, presence);
	}

	/**
	 * Sends a request that a new contact be added to the list of contacts.
	 * Additionally, requests authorization from that contact to receive updates
	 * any time the contact changes its status. This function does not add the
	 * user to the local list of contacts, which happens at the listening
	 * process once the server sends an update to the list of contacts as a
	 * result of this request.
	 * 
	 * @param contact
	 *            Contact that should be requested.
	 * @throws XMPPException
	 *             If there is a problem sending the request.
	 */
	public void sendNewContactRequest(Contact contact) throws XMPPException {

		/* YOUR CODE HERE */
		Element iq = xmppWriter.createElement("iq");
		iq.setAttribute("from", session.getUserJid());
		iq.setAttribute("id", this.getUniqueIdValue());
		iq.setAttribute("type", "set");

		Element query = xmppWriter.createElement("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		
		Element item = xmppWriter.createElement("item");
		item.setAttribute("jid", contact.getBareJid());
		item.setAttribute("group", "groupname");
		
		if(contact.getAlias() !=null){
			item.setAttribute("name", contact.getAlias());
		}
		
		query.appendChild(item);
		iq.appendChild(query);
		
		xmppWriter.writeIndividualElement(iq);
		System.out.println("\n" + "Request for contact: ");
		xmppWriter.debugElement(System.out, iq);
		//also simultaneously want to send a request for the subscription
		this.sendRequestForSubscription(contact);
		
	}

	private void sendRequestForSubscription(Contact contact) throws XMPPException {
		Element presence = xmppWriter.createElement("presence");
		presence.setAttribute("id", this.getUniqueIdValue());
		presence.setAttribute("to", contact.getBareJid());
		presence.setAttribute("type", "subscribe");
		
		xmppWriter.writeIndividualElement(presence);
		
		System.out.println("\n" + "THis will be the request for the presence that comes with the request for the contact");
		xmppWriter.debugElement(System.out, presence);
		
	}

	/**
	 * Sends a response message to a contact that requested authorization to
	 * receive updates when the local user changes its status.
	 * 
	 * @param jid
	 *            Jabber ID of the contact that requested authorization.
	 * @param accepted
	 *            <code>true</code> if the request was accepted by the user,
	 *            <code>false</code> otherwise.
	 * @throws XMPPException
	 *             If there was an error sending the response.
	 */
	public void respondContactRequest(String jid, boolean accepted)
			throws XMPPException {
		Element presence = xmppWriter.createElement("presence");
		presence.setAttribute("id", this.getUniqueIdValue());
		if(accepted){
			presence.setAttribute("type", "subscribed");
		}else{
			presence.setAttribute("type", "unsubscribed");
		}
		xmppWriter.writeIndividualElement(presence);
		
		/* YOUR CODE HERE */
	}

	/**
	 * Request that the server remove a specific contact from the list of
	 * contacts. Additionally, requests that no further status updates be sent
	 * regarding that contact, as well as that no further status updates about
	 * the local user be sent to that contact. This function does not remove the
	 * user from the local list of contacts, which happens at the listening
	 * process once the server sends an update to the list of contacts as a
	 * result of this request.
	 * 
	 * @param contact
	 *            Contact to be removed from the list of contacts.
	 * @throws XMPPException
	 *             If there was an error sending the request.
	 */
	public void removeAndUnsubscribeContact(Contact contact)
			throws XMPPException {
		Element iq = xmppWriter.createElement("iq");
		iq.setAttribute("id", this.getUniqueIdValue());
		iq.setAttribute("type", "set");
		Element query = xmppWriter.createElement("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		Element item = xmppWriter.createElement("item");
		item.setAttribute("jid", contact.getBareJid());
		item.setAttribute("subscription", "remove");
		query.appendChild(item);
		iq.appendChild(query);
	}

	/**
	 * Send a chat message to a specific contact.
	 * 
	 * @param message
	 *            Message to be sent.
	 * @throws XMPPException
	 *             If there was a problem sending the message.
	 */
	public void sendMessage(Message message) throws XMPPException {
		//TODO figure out a way to actually send the message
		/* YOUR CODE HERE */
		
		//creating element and setting manditory fields for this
		Element toSend = xmppWriter.createElement("message");
		toSend.setAttribute("type", "chat");	
		toSend.setAttribute("xml:lang", "en");
		toSend.setAttribute("id", this.getUniqueIdValue() + message.getTimestamp().getTime());
		
		//--- SETTING FROM ---
		Contact fromContact = message.getFrom();
		//if from is null then i'm not really sure what you do. i think you assume its the same user and resource		
		//RFC: where the value of the 'from' attribute MUST be the full JID (<localpart@domainpart/resource>)
		//determined by the server for the connected resource that generated the stanza (see Section 4.3.6), 
		//or the bare JID (<localpart@domainpart>) in the case of subscription-related presence stanzas 
		String fromJID = null;
		if(fromContact == null){
			fromJID = session.getUserJid();
		}else{
			fromJID = message.getFrom().getFullJid(); 
			
		}
		//now setting the from
		toSend.setAttribute("from", fromJID);
		
		//---SETTING TO----
		//If the message is being sent in reply to a message previously received from an address of the form 
		//<localpart@domainpart/resourcepart> (e.g., within the context of a one-to-one chat session as described under Section 5.1), 
		//the value of the 'to' address SHOULD be of the form <localpart@domainpart/resourcepart> rather than of the form <localpart@domainpart> 
		
		
		//set to attribute
		Contact to = message.getTo();
		String toJID = null;
		
		if(to == null){
			//NOT SURE IF WE HAVE TO DEAL WITH EMPTY TO CASE: if you don't have a reciever, i guess you could send it to yourself

			toJID = session.getUserJid();
			System.out.println("\n" + "Seems a bit strange to be sending a messge to yourself when you " +
					"have no to field. prob something is wrong");
		}else{
			if(session.getContact(message.getTo().getBareJid()) == null){
				System.out.println("You're sending a message to someone that isn't in your contacts... ");
			}

			Conversation prevConvo = session.getConversation(message.getTo());
			//if there is no conversation history with this contact then you should use the BAREJID
			if(prevConvo.getMessageList().size() < 1){
				toJID = to.getBareJid();
			}else{
				toJID = to.getFullJid();
				
			}
			//prevConvo.addOutgoingMessage(message);
		}
			
		
		//now setting the body
		toSend.setAttribute("to", toJID);					
		Element body = xmppWriter.createElement("body");
		body.setTextContent(message.getTextMessage());
		toSend.appendChild(body);
		xmppWriter.writeIndividualElement(toSend);
		System.out.println("\n" + "THis is what you're sending as the message info");
		xmppWriter.debugElement(System.out, toSend);
		
	}
}
