package com.choculaterie.models;

public record MinemevPostInfo(String uuid, String title, String description, String author, int downloads,
							  String createdAt, String[] tags, String[] versions, String vendor, String[] images,
							  String thumbnailUrl, String userPicture, String ytLink, String type) {
	public MinemevPostInfo(String uuid, String title, String description, String author,
						   int downloads, String createdAt, String[] tags, String[] versions, String vendor,
						   String[] images, String thumbnailUrl, String userPicture, String ytLink, String type) {
		this.uuid = uuid;
		this.title = title;
		this.description = description;
		this.author = author;
		this.downloads = downloads;
		this.createdAt = createdAt;
		this.tags = tags != null ? tags : new String[0];
		this.versions = versions != null ? versions : new String[0];
		this.vendor = vendor;
		this.images = images != null ? images : new String[0];
		this.thumbnailUrl = thumbnailUrl;
		this.userPicture = userPicture;
		this.ytLink = ytLink;
		this.type = type;
	}


}
