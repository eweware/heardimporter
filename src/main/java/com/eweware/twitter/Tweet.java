package com.eweware.twitter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ultradad on 3/24/15.
 */
public class Tweet
{
    public String created_at;
    public String id;
    public String id_str;
    public String text;
    public String source;
    public Boolean truncated;
    public User user;
    public Geo geo;
    public Coordinates coordinates;
    public Place place;
    public String contributors;
    public int retweet_count;
    public int favorite_count;
    public Entity entities;
    public Boolean favorited;
    public Boolean retweeted;
    public Boolean possibly_sensitive;
    public String lang;


    public String getPostedURL() {
        if ((entities.urls != null) && (entities.urls.size() > 0)) {
            TwitterURL theURL = entities.urls.get(0);
            return theURL.expanded_url;
        }
        else
            return null;
    }

    public Date getCreatedDate() {
        return getTwitterDate(created_at);
    }

    public static Date getTwitterDate(String date)  {
        Date theDate = null;
        try {
            final String TWITTER="EEE MMM dd HH:mm:ss ZZZZZ yyyy";
            SimpleDateFormat sf = new SimpleDateFormat(TWITTER);
            sf.setLenient(true);
            theDate = sf.parse(date);

        } catch (ParseException exp) {
            theDate = null;
        }

        return theDate;

    }

    public Boolean hasMentions() {
        return text.contains("@");
    }

    public String getTweetTitle(Boolean stripHashTags, Boolean stripURLs, Boolean stripMentions) {
        String sourceStr = text;

        if (stripURLs) {
            sourceStr = removeUrl(sourceStr);
        }


        if (stripHashTags) {
            final String regex = "#[^\\s]+";
            sourceStr = sourceStr.replaceAll(regex, "");
        }

        if (stripMentions) {
            final String regex = "@[^\\s]+";
            sourceStr = sourceStr.replaceAll(regex, "");
        }

        return sourceStr;
    }

    public Boolean isRetweet() {
        return text.contains("RT");
    }

    private String removeUrl(String sourceStr)
    {
        String urlPattern = "((https?|ftp|gopher|telnet|file|Unsure|http):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
        Pattern p = Pattern.compile(urlPattern, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sourceStr);
        int i = 0;
        while (m.find()) {
            sourceStr = sourceStr.replaceAll(m.group(i),"").trim();
            i++;
        }
        return sourceStr;
    }

}