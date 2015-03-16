
package com.eweware.feedimport;

import java.util.List;

public class Images{
   	private String caption;
   	private List<Colors> colors;
   	private Number entropy;
   	private Number height;
   	private Number size;
   	private String url;
   	private Number width;

 	public String getCaption(){
		return this.caption;
	}
	public void setCaption(String caption){
		this.caption = caption;
	}
 	public List getColors(){
		return this.colors;
	}
	public void setColors(List colors){
		this.colors = colors;
	}
 	public Number getEntropy(){
		return this.entropy;
	}
	public void setEntropy(Number entropy){
		this.entropy = entropy;
	}
 	public Number getHeight(){
		return this.height;
	}
	public void setHeight(Number height){
		this.height = height;
	}
 	public Number getSize(){
		return this.size;
	}
	public void setSize(Number size){
		this.size = size;
	}
 	public String getUrl(){
		return this.url;
	}
	public void setUrl(String url){
		this.url = url;
	}
 	public Number getWidth(){
		return this.width;
	}
	public void setWidth(Number width){
		this.width = width;
	}
}
