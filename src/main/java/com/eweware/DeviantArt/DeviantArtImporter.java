package com.eweware.DeviantArt;

import com.eweware.feedimport.Entry;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.gson.Gson;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ultradad on 3/26/15.
 */
public class DeviantArtImporter {
    private static final Logger log = Logger.getLogger(DeviantArtImporter.class.getName());
    private static AuthToken daAuth = null;
    private static final String apibase = "https://www.deviantart.com/api/v1/oauth2/";


    public DeviantArtImporter() {
        try {
            init();
        }
        catch (Exception exp) {

        }
    }

    private void init() throws java.io.IOException {
        if (daAuth == null)
            daAuth = requestBearerToken("https://www.deviantart.com/oauth2/token?client_id=2424&client_secret=03eea2ab4282d59692538b89deacc13e&grant_type=client_credentials");
    }




    // browse/undiscovered
    public List<Entry> DoDAImport(String queryPath, int maxCount) {
        List<Entry> entries = null;
        DeviationResultList resultList = null;

        if ((daAuth != null) && (!daAuth.access_token.isEmpty()))
        {
            try {
                String limitStr = "limit=" + maxCount;
                String tokenStr = "access_token=" + daAuth.access_token;

                String url = apibase + queryPath + "?" + limitStr +"&" + tokenStr;
                URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

                HTTPResponse response = fetchService.fetch(new URL(url));

                byte[] embedData = response.getContent();
                String resultStr = new String(embedData, "UTF-8");
                if (response.getResponseCode() / 100 == 2) {
                    resultList = new Gson().fromJson(resultStr, DeviationResultList.class);
                }
            } catch (Exception exp) {
                log.log(Level.SEVERE, exp.toString(), exp);
            }
        }


        if ((resultList != null) && (resultList.results.size() > 0)) {
            entries = new ArrayList<Entry>();

            for (Deviation curDev : resultList.results) {
                Entry newEntry = new Entry();
                String titleStr = curDev.title + "\n" + "by " + curDev.author.username + "\n\n";
                newEntry.setTitle(titleStr);
                newEntry.setLink(curDev.url);
                newEntry.setImage(curDev.content.src);
                entries.add(newEntry);
                if (entries.size() == maxCount)
                    break;
            }
        }

        return entries;
    }

    // Constructs the request for requesting a bearer token and returns that token as a string
    private AuthToken requestBearerToken(String endPointUrl) throws IOException {
        try {
            URLFetchService fetchService = URLFetchServiceFactory.getURLFetchService();

            HTTPResponse response = fetchService.fetch(new URL(endPointUrl));

            byte[] embedData = response.getContent();
            String resultStr = new String(embedData, "UTF-8");
            if (response.getResponseCode() / 100 == 2) {
                AuthToken newToken = new Gson().fromJson(resultStr, AuthToken.class);
                return newToken;
            }
        } catch (Exception exp) {
            log.log(Level.SEVERE, exp.toString(), exp);
        }
        return null;

    }

}
