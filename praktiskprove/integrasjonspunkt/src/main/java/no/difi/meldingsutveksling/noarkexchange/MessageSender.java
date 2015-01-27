package no.difi.meldingsutveksling.noarkexchange;


import com.thoughtworks.xstream.XStream;
import no.difi.meldingsutveksling.adresseregister.client.CertificateNotFoundException;
import no.difi.meldingsutveksling.dokumentpakking.Dokumentpakker;
import no.difi.meldingsutveksling.domain.*;
import no.difi.meldingsutveksling.domain.sbdh.Scope;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.eventlog.Event;
import no.difi.meldingsutveksling.eventlog.EventLog;
import no.difi.meldingsutveksling.noarkexchange.schema.*;
import no.difi.meldingsutveksling.services.AdresseregisterMock;
import no.difi.meldingsutveksling.services.AdresseregisterService;
import no.difi.meldingsutveksling.transport.Transport;
import no.difi.meldingsutveksling.transport.TransportFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import static no.difi.meldingsutveksling.noarkexchange.StandardBusinessDocumentFactory.create;

@Component
public class MessageSender {

    private static final String JP_ID = "jpId";
    private static final String DATA = "data";

    @Autowired
    EventLog eventLog;

    @Autowired
    @Qualifier("multiTransport")
    TransportFactory transportFactory;

    @Autowired
    AdresseregisterService adresseregister;

    private final Dokumentpakker dokumentpakker;

    public MessageSender(Dokumentpakker dokumentpakker, AdresseregisterService adresseregister) {
        this.dokumentpakker = dokumentpakker;
        this.adresseregister = adresseregister;
    }

    public MessageSender() {
        this(new Dokumentpakker(), new AdresseregisterMock());
    }

    boolean setSender(IntegrasjonspunktContext context, AddressType sender) {
        if (sender == null) {
            return false;
        }
        Avsender avsender;
        Certificate sertifikat;
        try {
            sertifikat = adresseregister.getCertificate(sender.getOrgnr());
        } catch (CertificateNotFoundException e) {
            eventLog.log(new Event().setExceptionMessage(e.toString()));
            return false;
        }
        avsender = Avsender.builder(new Organisasjonsnummer(sender.getOrgnr()), new Noekkelpar(findPrivateKey(), sertifikat)).build();
        context.setAvsender(avsender);
        return true;
    }

    boolean setRecipient(IntegrasjonspunktContext context, AddressType receiver) {
        if (receiver == null) {
            return false;
        }
        try {
            Certificate sertifikat = adresseregister.getCertificate(receiver.getOrgnr());

        } catch (CertificateNotFoundException e) {
            eventLog.log(new Event().setExceptionMessage(e.toString()));
            return false;
        }
        return true;
    }

    public PutMessageResponseType sendMessage(PutMessageRequestType message) {

        String conversationId = message.getEnvelope().getConversationId();
        String journalPostId = getJpId(message);

        IntegrasjonspunktContext context = new IntegrasjonspunktContext();
        context.setJpId(journalPostId);

        EnvelopeType envelope = message.getEnvelope();
        if (envelope == null) {
            return createErrorResponse("Missing envelope");
        }

        AddressType receiver = message.getEnvelope().getReceiver();
        if (setRecipient(context, receiver)) {
            return createErrorResponse("invalid recipient, no recipient or missing certificate for " + receiver.getOrgnr());
        }

        AddressType sender = message.getEnvelope().getSender();
        if (setSender(context, sender)) {
            return createErrorResponse("invalid sender, no sender or missing certificate for " + receiver.getOrgnr());
        }
        eventLog.log(new Event(ProcessState.SIGNATURE_VALIDATED));

        StandardBusinessDocument sbd = create(message, context.getAvsender(), context.getMottaker());
        Scope item = sbd.getStandardBusinessDocumentHeader().getBusinessScope().getScope().get(0);
        String hubCid = item.getInstanceIdentifier();
        eventLog.log(new Event().setJpId(journalPostId).setArkiveConversationId(conversationId).setHubConversationId(hubCid).setProcessStates(ProcessState.CONVERSATION_ID_LOGGED));

        Transport t = transportFactory.createTransport(sbd);
        t.send(sbd);

        eventLog.log(createOkStateEvent(message));
        return new PutMessageResponseType();
    }

    private String getJpId(PutMessageRequestType message) {
        Document document = getDocument(message);
        NodeList messageElement = document.getElementsByTagName(JP_ID);
        if (messageElement.getLength() == 0) {
            throw new MeldingsUtvekslingRuntimeException("no " + JP_ID + " element in document ");
        }
        return messageElement.item(0).getTextContent();
    }

    private Document getDocument(PutMessageRequestType message) throws MeldingsUtvekslingRuntimeException {
        DocumentBuilder documentBuilder = getDocumentBuilder();
        Element element = (Element) message.getPayload();
        NodeList nodeList = element.getElementsByTagName(DATA);
        if (nodeList.getLength() == 0) {
            throw new MeldingsUtvekslingRuntimeException("no " + DATA + " element in payload");
        }
        Node payloadData = nodeList.item(0);
        String payloadDataTextContent = payloadData.getTextContent();
        Document document;

        try {
            document = documentBuilder.parse(new InputSource(new ByteArrayInputStream(payloadDataTextContent.getBytes("utf-8"))));
        } catch (SAXException | IOException e) {
            throw new MeldingsUtvekslingRuntimeException(e);
        }
        return document;
    }

    private DocumentBuilder getDocumentBuilder() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new MeldingsUtvekslingRuntimeException(e);
        }
        return builder;
    }


    private PutMessageResponseType createErrorResponse(String message) {
        PutMessageResponseType response = new PutMessageResponseType();
        AppReceiptType receipt = new AppReceiptType();
        receipt.setType(message);
        response.setResult(receipt);
        return response;
    }


    //todo refactor
    PrivateKey findPrivateKey() {
        PrivateKey key;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("knutepunkt_privatekey.pkcs8"),
                Charset.forName("UTF-8")))) {
            StringBuilder builder = new StringBuilder();
            boolean inKey = false;
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                if (!inKey && line.startsWith("-----BEGIN ") && line.endsWith(" PRIVATE KEY-----")) {
                    inKey = true;
                } else {
                    if (line.startsWith("-----END ") && line.endsWith(" PRIVATE KEY-----")) {
                        inKey = false;
                    }
                    builder.append(line);
                }
            }

            byte[] encoded = DatatypeConverter.parseBase64Binary(builder.toString());
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            key = kf.generatePrivate(keySpec);

        } catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException e) {
            throw new MeldingsUtvekslingRuntimeException(e);
        }
        return key;
    }

    public void setAdresseregister(AdresseregisterService adresseregister) {
        this.adresseregister = adresseregister;
    }

    public void setEventLog(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    private Event createErrorEvent(PutMessageRequestType anyOject, Exception e) {
        XStream xs = new XStream();
        Event event = new Event();
        event.setSender(anyOject.getEnvelope().getSender().getOrgnr());
        event.setReceiver(anyOject.getEnvelope().getReceiver().getOrgnr());
        event.setExceptionMessage(event.getMessage());
        event.setProcessStates(ProcessState.MESSAGE_SEND_FAIL);
        event.setMessage(xs.toXML(anyOject));
        return event;
    }

    private Event createOkStateEvent(PutMessageRequestType anyOject) {
        XStream xs = new XStream();
        Event event = new Event();
        event.setSender(anyOject.getEnvelope().getSender().getOrgnr());
        event.setReceiver(anyOject.getEnvelope().getReceiver().getOrgnr());
        event.setMessage(xs.toXML(anyOject));
        event.setProcessStates(ProcessState.SBD_SENT);
        return event;
    }

}

