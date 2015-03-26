package com.eweware.heard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by ultradad on 3/10/15.
 */
public class ImportRecord {
    public String _id;
    public Boolean autoimport;
    public Boolean importasuser;
    public Boolean appendurl;
    public Boolean summarizepage;
    public String channel;
    public String importusername;
    public String importpassword;

    // rss fields
    public String RSSurl;
    public Boolean usefeedimage;
    public String titlefield;
    public String imagefield;
    public String bodyfield;
    public String urlfield;

    // twitter fields
    public String twittername;

    // DeviantArt fields
    public String searchpath;

    public String lastimport;
    public Integer importfrequency;
    public Integer feedtype;

    public Date getLastImportDate() {
        Date theDate;

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
            theDate = dateFormat.parse(lastimport);
        }
        catch (Exception exp) {
            theDate = null;
        }


        return theDate;
    }
}
