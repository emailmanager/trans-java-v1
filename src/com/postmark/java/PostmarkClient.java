// The MIT License
//
// Copyright (c) 2010 Jared Holdcroft
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.emailmanager.java;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.joda.time.DateTime;

import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Emailmanager for Java
 * <p/>
 * This library can be used to leverage the emailmanager.com functionality from a Java client
 * <p/>
 *
 * @see <a href="http://github.com/jaredholdcroft/emailmanager-java"/>
 */

// Class that does the heavy lifting
public class EmailmanagerClient {

    private static Logger logger = Logger.getLogger("com.emailmanager.java");
    private String serverToken;

    private static GsonBuilder gsonBuilder = new GsonBuilder();

    static {
        gsonBuilder.registerTypeAdapter(DateTime.class, new DateTimeTypeAdapter());
        gsonBuilder.setPrettyPrinting();
        gsonBuilder.setExclusionStrategies(new SkipMeExclusionStrategy(Boolean.class));

        logger.addHandler(new ConsoleHandler());
        logger.setLevel(Level.ALL);
    }


    /**
     * Initializes a new instance of the EmailmanagerClient class.
     * <p/>
     * If you do not have a server token you can request one by signing up to
     * use Emailmanager: http://emailmanager.com.
     *
     * @param serverToken the emailmanager server token
     */
    public EmailmanagerClient(String serverToken) {

        this.serverToken = serverToken;

    }

    /**
     * Sends a message through the Emailmanager API.
     * All email addresses must be valid, and the sender must be
     * a valid sender signature according to Emailmanager. To obtain a valid
     * sender signature, log in to Emailmanager and navigate to:
     * http://emailmanager.com/signatures.
     *
     * @param from    An email address for a sender
     * @param to      An email address for a recipient
     * @param replyTo An email address for the reply-to
     * @param cc      An email address for CC
     * @param subject The message subject line
     * @param body    The message body
     * @param isHTML  Is the body text HTML
     * @param tag     A tag to identify the message in emailmanager
     * @return {@link EmailmanagerResponse} with details about the transaction
     */
    public EmailmanagerResponse sendMessage(String from, String to, String replyTo, String cc, String subject, String body, boolean isHTML, String tag) throws EmailmanagerException {
        return sendMessage(from, to, replyTo, cc, subject, body, isHTML, tag, null);
    }

    /**
     * Sends a message through the Emailmanager API.
     * All email addresses must be valid, and the sender must be
     * a valid sender signature according to Emailmanager. To obtain a valid
     * sender signature, log in to Emailmanager and navigate to:
     * http://emailmanager.com/signatures.
     *
     * @param from    An email address for a sender
     * @param to      An email address for a recipient
     * @param replyTo An email address for the reply-to
     * @param cc      An email address for CC
     * @param subject The message subject line
     * @param body    The message body
     * @param isHTML  Is the body text HTML
     * @param tag     A tag to identify the message in emailmanager
     * @param headers A collection of additional mail headers to send with the message
     * @return {@link EmailmanagerResponse} with details about the transaction
     */
    public EmailmanagerResponse sendMessage(String from, String to, String replyTo, String cc, String subject, String body, boolean isHTML, String tag, List<NameValuePair> headers) throws EmailmanagerException {
        EmailmanagerMessage message = new EmailmanagerMessage(from, to, replyTo, subject, cc, body, isHTML, tag, headers);

        return sendMessage(message);
    }

    /**
     * Sends a message through the Emailmanager API.
     * All email addresses must be valid, and the sender must be
     * a valid sender signature according to Emailmanager. To obtain a valid
     * sender signature, log in to Emailmanager and navigate to:
     * http://emailmanager.com/signatures.
     *
     * @param message A prepared message instance.</param>
     * @return A response object
     */
    public EmailmanagerResponse sendMessage(EmailmanagerMessage message) throws EmailmanagerException {

        HttpClient httpClient = new DefaultHttpClient();
        EmailmanagerResponse theResponse = new EmailmanagerResponse();

        try {

            // Create post request to Emailmanager API endpoint
            HttpPost method = new HttpPost("http://trans.emailmanager.com/email");

            // Add standard headers required by Emailmanager
            method.addHeader("Accept", "application/json");
            method.addHeader("Content-Type", "application/json; charset=utf-8");
            method.addHeader("X-Emailmanager-Server-Token", serverToken);
            method.addHeader("User-Agent", "Emailmanager-Java");

            // Validate and clean the message
            message.validate();
            message.clean();

            // Convert the message into JSON content
            Gson gson = gsonBuilder.create();
            String messageContents = gson.toJson(message);
            logger.info("Message contents: " + messageContents);

            // Add JSON as payload to post request
            StringEntity payload = new StringEntity(messageContents, "UTF-8");
            method.setEntity(payload);


            ResponseHandler<String> responseHandler = new BasicResponseHandler();

            try {
                String response = httpClient.execute(method, responseHandler);
                logger.info("Message response: " + response);
                theResponse = gsonBuilder.create().fromJson(response, EmailmanagerResponse.class);
                theResponse.status = EmailmanagerStatus.SUCCESS;
            } catch (HttpResponseException hre) {
                switch(hre.getStatusCode()) {
                    case 401:
                    case 422:
                        logger.log(Level.SEVERE, "There was a problem with the email: " + hre.getMessage());
                        theResponse.setMessage(hre.getMessage());
                        theResponse.status = EmailmanagerStatus.USERERROR;
                        throw new EmailmanagerException(hre.getMessage(), theResponse);
                    case 500:
                        logger.log(Level.SEVERE, "There has been an error sending your email: " + hre.getMessage());
                        theResponse.setMessage(hre.getMessage());
                        theResponse.status = EmailmanagerStatus.SERVERERROR;
                        throw new EmailmanagerException(hre.getMessage(), theResponse);
                    default:
                        logger.log(Level.SEVERE, "There has been an unknow error sending your email: " + hre.getMessage());
                        theResponse.status = EmailmanagerStatus.UNKNOWN;
                        theResponse.setMessage(hre.getMessage());
                        throw new EmailmanagerException(hre.getMessage(), theResponse);

                }
            }


        } catch (Exception e) {
            logger.log(Level.SEVERE, "There has been an error sending your email: " + e.getMessage());
            throw new EmailmanagerException(e);
        }
        finally {

            httpClient.getConnectionManager().shutdown();
        }

        return theResponse;
    }


}
