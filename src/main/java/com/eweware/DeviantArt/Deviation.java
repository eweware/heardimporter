package com.eweware.DeviantArt;

import java.util.List;

/**
 * Created by ultradad on 3/26/15.
 */
public class Deviation {
    public String deviationid;
    public Object printid;
    public String url;
    public String title;
    public String category;
    public String category_path;
    public Boolean is_favourited;
    public Boolean is_deleted;
    public Author author;
    public Stats stats;
    public int published_time;
    public Boolean allows_comments;
    public Preview preview;
    public Content content;
    public List<Thumb> thumbs;
}
