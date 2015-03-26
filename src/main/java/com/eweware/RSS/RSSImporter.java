package com.eweware.RSS;

import com.eweware.feedimport.Entry;
import com.eweware.feedimport.MediaContent;
import com.eweware.feedimport.MediaGroup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ultradad on 3/26/15.
 */
public class RSSImporter {
    private static final Logger log = Logger.getLogger(RSSImporter.class.getName());

    public RSSImporter() {
        Init();
    }

    private void Init() {
        // Init the reader...
    }

    private String extractNextPageURL(Node root, String sourceURL) {
        String nextURL = null;
        try {
            XPathFactory xFactory = XPathFactory.newInstance();
            XPath xPath = xFactory.newXPath();

            XPathExpression xExpress = xPath.compile("/rss/channel/*[local-name()='link'][@rel='next']");
            NodeList nodes = (NodeList) xExpress.evaluate(root, XPathConstants.NODESET);
            if ((nodes != null) && (nodes.getLength() > 0)) {
                final String urlPart = nodes.item(0).getAttributes().getNamedItem("href").getNodeValue();
                final URL baseURL = new URL(sourceURL);
                final String protocol = baseURL.getProtocol();
                final String hostStr = baseURL.getHost();
                final URL newURL = new URL(protocol, hostStr, urlPart);
                nextURL = newURL.toString();
            }
            log.log(Level.INFO, "found " + nodes.getLength() + " atom:link nodes");
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        return nextURL;
    }

    private List<Entry> extractPageEntries(Node root) {
        List<Entry> entries = null;
        try {

            XPathFactory xFactory = XPathFactory.newInstance();
            XPath xPath = xFactory.newXPath();

            XPathExpression xExpress = xPath.compile("/rss/channel/item");
            NodeList nodes = (NodeList) xExpress.evaluate(root, XPathConstants.NODESET);
            if ((nodes != null) && (nodes.getLength() > 0)) {
                entries = new ArrayList<Entry>();
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node nNode = nodes.item(i);
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element curEl = (Element)nNode;

                        Entry newEntry = new Entry();
                        String theURL = curEl.getElementsByTagName("link").item(0).getTextContent();
                        String theDate = curEl.getElementsByTagName("pubDate").item(0).getTextContent();
                        newEntry.setLink(theURL);
                        newEntry.setPublishedDate(theDate);
                        entries.add(newEntry);
                    }
                }
            }
            log.log(Level.INFO, "found " + nodes.getLength() + " entries");
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        return entries;
    }

    public List<Entry> parseRSSFeed(String feedStr, Date cutoffDate, Boolean useSourceImage, Integer maxCount) {
        List<Entry> entries = new ArrayList<Entry>();

        appendRSSFeed(entries, feedStr, cutoffDate, useSourceImage, maxCount);

        return entries;
    }

    private void appendRSSFeed(List<Entry> entries, String feedStr, Date cutoffDate, Boolean useSourceImage, Integer maxCount) {
        String nextURL = null;

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(feedStr);
            doc.getDocumentElement().normalize();
            Node root = doc.getDocumentElement();

            nextURL = extractNextPageURL(root, feedStr);
            List<Entry> theEntries = extractPageEntries(root);

            // we have data, now load it!
            for (Entry curEntry : theEntries) {
                if ((cutoffDate == null) || (curEntry.getPublishedDate().after(cutoffDate))) {
                    entries.add(curEntry);
                    if (entries.size() == maxCount)
                        break;
                }
            }

        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }

        if ((nextURL != null) && (entries.size() < maxCount))
            appendRSSFeed(entries, nextURL, cutoffDate, useSourceImage, maxCount);

    }
}
