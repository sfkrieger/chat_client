/*
 * University of British Columbia
 * Department of Computer Science
 * CPSC317 - Internet Programming
 * Assignment 1
 * 
 * Author: Jonatan Schroeder
 * January 2012
 * 
 * This code may not be used without written consent of the authors, except for 
 * current and future projects and assignments of the CPSC317 course at UBC.
 */

package ubc.cs317.xmpp.model;

import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ubc.cs317.xmpp.exception.XMPPException;
import ubc.cs317.xmpp.model.listener.ContactListener;
import ubc.cs317.xmpp.model.listener.MessageListener;
import ubc.cs317.xmpp.model.listener.SessionListener;
import ubc.cs317.xmpp.model.listener.SubscriptionRequestListener;
import ubc.cs317.xmpp.net.XMPPConnection;

/**
 * This class manages an open XMPP session with a server. It provides the main
 * interaction between the network connection interface (package
 * ubc.cs317.xmpp.net) and the interfaces that provide the interaction with the
 * users, such as GUIs.
 */
public class Session {

	private XMPPConnection connection;
	private boolean isConnectionClosed = false;

	private Map<String, Contact> contacts = new HashMap<String, Contact>();
	private Map<Contact, Conversation> conversations = new HashMap<Contact, Conversation>();

	private Set<ContactListener> contactListeners = new HashSet<ContactListener>();
	private Set<SubscriptionRequestListener> subscriptionRequestListeners = new HashSet<SubscriptionRequestListener>();
	private Set<MessageListener> messageListeners = new HashSet<MessageListener>();
	private Set<SessionListener> sessionListeners = new HashSet<SessionListener>();

	private String userJid;
	private ContactStatus status;

	/**
	 * Creates a new XMPP session. This constructor will create a new
	 * XMPPConnection instance, which will handle the connection establishment.
	 * It will also request that the list of contacts be provided and send the
	 * initial status.
	 * 
	 * @param jidUser
	 *            Local part of the user JID.
	 * @param jidDomain
	 *            Domain part of the user JID.
	 * @param resource
	 *            Specific resource name to be used in this session. Optional,
	 *            if resource is null or empty, a resource name will be
	 *            generated by the server.
	 * @param password
	 *            User password.
	 * @param status
	 *            Initial status.
	 * @throws XMPPException
	 *             If it was not possible to establish the connection, or if
	 *             there was a problem trying to send initial messages.
	 */
	public Session(String jidUser, String jidDomain, String resource,
			String password, ContactStatus status) throws XMPPException {

		this.setUserJid(jidUser + "@" + jidDomain);

		connection = new XMPPConnection(jidUser, jidDomain, resource, password,
				this);

		/*
		 * Session requests for the contact list, but doesn't wait until it is
		 * received to proceed. Once the contact list is received, the
		 * connection class will call methods in this class to add the contacts
		 * in the roster.
		 */
		connection.sendRequestForContactList();
		setAndSendCurrentStatus(status);
	}

	/**
	 * Adds a new listener interface to be called every time a contact is added,
	 * removed or changed. Any interaction with user interfaces is done through
	 * these listeners.
	 * 
	 * @param listener
	 *            A ContactListener to be called when a contact event happens.
	 */
	public void addContactListener(ContactListener listener) {
		contactListeners.add(listener);
		for (Contact contact : contacts.values())
			contact.addContactListener(listener);
	}

	/**
	 * Adds a new listener interface to be called every time another user
	 * requests to be subscribed. Any interaction with user interfaces is done
	 * through these listeners.
	 * 
	 * @param listener
	 *            A SubscriptionRequestListener to be called when a subscription
	 *            request event happens.
	 */
	public void addSubscriptionRequestListener(
			SubscriptionRequestListener listener) {
		subscriptionRequestListeners.add(listener);
	}

	/**
	 * Adds a new listener interface to be called every time a new message is
	 * received. Any interaction with user interfaces is done through these
	 * listeners.
	 * 
	 * @param listener
	 *            A MessageListener to be called when a message event happens.
	 */
	public synchronized void addMessageListener(MessageListener listener) {
		messageListeners.add(listener);
		for (Conversation conversation : conversations.values())
			conversation.addMessageListener(listener);
	}

	/**
	 * Adds a new listener interface to be called every time a session event
	 * (such as a reading exception or close) happens. Any interaction with user
	 * interfaces is done through these listeners.
	 * 
	 * @param listener
	 *            A SessionListener to be called when a session event happens.
	 */
	public synchronized void addSessionListener(SessionListener listener) {
		sessionListeners.add(listener);
	}

	/**
	 * Removes an existing listener from the list of listeners to be called for
	 * session events.
	 * 
	 * @param listener
	 *            A SessionListener that should no longer be called when a
	 *            session event happens.
	 */
	public void removeSessionListener(SessionListener listener) {
		sessionListeners.remove(listener);
	}

	/**
	 * Adds a contact received from the network connection to the local list of
	 * contacts. This method should be called every time a query is received
	 * from the server with information about a contact that did not yet exist
	 * in the list.
	 * 
	 * @param contact
	 *            Contact to be added to the list.
	 * @throw InvalidParameterException If there is a contact in the list with
	 *        the same Jabber ID.
	 */
	public void addReceivedContact(Contact contact) {

		if (contacts.containsKey(contact.getBareJid()))
			throw new InvalidParameterException("Contact already exists.");

		contacts.put(contact.getBareJid(), contact);
		for (ContactListener listener : contactListeners) {
			listener.contactAdded(contact);
			contact.addContactListener(listener);
		}
	}

	/**
	 * Returns the current local copy of the list of contacts (roster). This
	 * method returns an unmodifiable list, so the result of this function
	 * cannot be used to add or remove contacts in the list.
	 * 
	 * @return A current collection corresponding to the local copy of the list
	 *         of contacts.
	 */
	public Collection<Contact> getContacts() {
		return Collections.unmodifiableCollection(contacts.values());
	}

	/**
	 * Returns the conversation associated to a specific contact. If there is a
	 * current conversation already started during this session with this
	 * contact, returns the existing conversation, otherwise creates a new
	 * conversation.
	 * 
	 * @param contact
	 *            The contact for whom the conversation should be retrieved.
	 * @return A conversation associated to the contact.
	 */
	public synchronized Conversation getConversation(Contact contact) {

		if (contact == null)
			throw new NullPointerException();

		Conversation conversation = conversations.get(contact);
		if (conversation == null) {
			conversation = new Conversation(this, contact);
			for (MessageListener listener : messageListeners)
				conversation.addMessageListener(listener);
			conversations.put(contact, conversation);
		}
		return conversation;
	}

	/**
	 * Returns the current status associated to the local user.
	 * 
	 * @return A ContactStatus associated to the user.
	 */
	public ContactStatus getCurrentStatus() {
		return this.status;
	}

	/**
	 * Sets the current status of the user to a new value, and sends this new
	 * status to the server to be broadcast to subscribed contacts.
	 * 
	 * @param status
	 *            The user's new status.
	 * @throws XMPPException
	 *             If there was a problem sending the current status to the
	 *             server.
	 */
	public void setAndSendCurrentStatus(ContactStatus status)
			throws XMPPException {
		if (status == null)
			throw new NullPointerException();
		this.status = status;
		connection.sendCurrentStatus();
	}

	/**
	 * Returns the contact associated to the informed Jabber ID. If there is no
	 * such contact, returns null. If a full Jabber ID is informed (i.e., with
	 * resource), the resource is ignored.
	 * 
	 * @param jid
	 *            The contact's Jabber ID.
	 * @return The contact associated to the informed JID, or null if such a
	 *         contact is not currently on the local roster.
	 */
	public Contact getContact(String jid) {
		return contacts.get(jid.split("/")[0]);
	}

	/**
	 * Returns the current full JID for the logged in user, as returned by the
	 * resource binding operation.
	 * 
	 * @return Full JID for the user.
	 */
	public String getUserJid() {
		return this.userJid;
	}

	/**
	 * Returns the bare JID for the logged in user (i.e. without the resource
	 * name).
	 * 
	 * @return Bare JID for the user.
	 */
	public String getUserBareJid() {
		return this.userJid.split("/")[0];
	}

	/**
	 * Sets the full JID for the logged in user. This JID is sent by the server
	 * after a successful resource binding operation.
	 * 
	 * @param jid
	 *            Full JID for the user.
	 */
	public void setUserJid(String jid) {
		if (jid == null)
			throw new NullPointerException();
		this.userJid = jid;
	}

	/**
	 * Sends a message to the server, to be delivered to a specific contact.
	 * 
	 * @param message
	 *            Message object containing the message to be delivered.
	 * @throws XMPPException
	 *             If there is an error while trying to send the message.
	 */
	public void sendMessage(Message message) throws XMPPException {
		connection.sendMessage(message);
	}

	/**
	 * Sends a request to the server that a contact be added to the list of
	 * contacts and subscribed to. This method does not directly add the contact
	 * to the list, which is done by <code>Session.addReceivedContact</code>
	 * once a confirmation is sent from the server.
	 * 
	 * @param contact
	 *            Contact to be added to the list of contacts.
	 * @throws XMPPException
	 *             If there is an error while trying to send the request.
	 */
	public void sendNewContactRequest(Contact contact) throws XMPPException {
		connection.sendNewContactRequest(contact);
	}

	/**
	 * Handles a received subscription request from a contact that wants to
	 * receive presence notification from the user. The only action taken in
	 * this method is to call the corresponding listeners, which will provide a
	 * way of determining if this request should be accepted and call
	 * <code>Session.respondContactRequest</code>.
	 * 
	 * @param jid
	 *            JID of the contact that requested subscription.
	 */
	public void handleReceivedSubscriptionRequest(String jid) {
		for (SubscriptionRequestListener listener : subscriptionRequestListeners)
			listener.subscriptionRequested(jid);
	}

	/**
	 * Responds to a subscription request by indicating if the request was
	 * accepted or denied.
	 * 
	 * @param jid
	 *            JID of the contact whose request is being responded to.
	 * @param accepted
	 *            <code>true</code> if the request was accepted,
	 *            <code>false</code> if the request was denied.
	 * @throws XMPPException
	 *             If there is a problem sending the response.
	 */
	public void respondContactRequest(String jid, boolean accepted)
			throws XMPPException {
		connection.respondContactRequest(jid, accepted);
	}

	/**
	 * Sends a request to remove a contact from the list of contacts. Also
	 * unsubscribes from the contact's presence notification and removes the
	 * user's presence notification for the contact.
	 * 
	 * @param contact
	 *            Contact to be removed.
	 * @throws XMPPException
	 *             If there is a problem sending the request.
	 */
	public void sendRequestToRemoveContact(Contact contact)
			throws XMPPException {
		connection.removeAndUnsubscribeContact(contact);
	}

	/**
	 * Processes an exception received by the connection handler and not
	 * otherwise handled by other functions, most notably exceptions that happen
	 * while receiving messages in asynchronous message reading.
	 * 
	 * @param exception
	 *            The exception thrown by the connection
	 */
	public void processReceivedException(XMPPException exception) {
		for (SessionListener listener : sessionListeners)
			listener.readingExceptionThrown(exception);
	}

	/**
	 * Closes the connection, if it is not yet closed.
	 */
	public synchronized void closeConnection() {

		if (isConnectionClosed)
			return;
		try {
			setAndSendCurrentStatus(ContactStatus.OFFLINE);
		} catch (XMPPException e) {
		} finally {
			connection.closeConnection();
			isConnectionClosed = true;
		}
		
		for (SessionListener listener : sessionListeners)
			listener.sessionClosed();
	}

	public void removeContact(Contact contact) {
		contacts.remove(contact.getBareJid());
		for (ContactListener listener : contactListeners)
			listener.contactRemoved(contact);
	}

}
