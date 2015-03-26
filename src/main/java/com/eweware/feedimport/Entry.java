
package com.eweware.feedimport;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Entry {
    public String link;
    public String publishedDate;
    public String title;
    public String image;



    public String getImage(){ return this.image; }
    public void setImage(String newImage){
        this.image = newImage;
    }


 	public String getLink(){
		return this.link;
	}
	public void setLink(String link){
		this.link = link;
	}

 	public Date getPublishedDate(){
        Date theDate = null;
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        try {
            theDate = dateFormat.parse(this.publishedDate);
        } catch (ParseException exp)
        {
            System.err.println("Error Parsing date: " + this.publishedDate);
            theDate = null;
        }
        return theDate;
	}
	public void setPublishedDate(String publishedDate){
		this.publishedDate = publishedDate;
	}

 	public String getTitle(){
		return this.title;
	}
	public void setTitle(String title){
		this.title = title;
	}
}
