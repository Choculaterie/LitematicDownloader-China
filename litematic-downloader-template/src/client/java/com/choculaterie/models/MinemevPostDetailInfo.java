package com.choculaterie.models;

public class MinemevPostDetailInfo {
	private final String uuid;
	private final String title;
	private final String description;
	private final String descriptionMd;
	private final String author;
	private final int downloads;
	private final String createdAt;
	private final String[] tags;
	private final String[] versions;
	private final String[] images;
	private final String ytLink;
	private final boolean owner;
	private final String creators;
	private final String type;
	private final java.util.Map<String, java.util.List<String>> conditions;
	private final boolean hasX;
	private final boolean hasY;
	private final boolean hasZ;

	public MinemevPostDetailInfo(String uuid, String title, String description, String descriptionMd,
								 String author, int downloads, String createdAt, String[] tags,
								 String[] versions, String[] images, String ytLink, boolean owner, String creators, String type,
								 java.util.Map<String, java.util.List<String>> conditions, boolean hasX, boolean hasY, boolean hasZ) {
		this.uuid = uuid;
		this.title = title;
		this.description = description;
		this.descriptionMd = descriptionMd;
		this.author = author;
		this.downloads = downloads;
		this.createdAt = createdAt;
		this.tags = tags != null ? tags : new String[0];
		this.versions = versions != null ? versions : new String[0];
		this.images = images != null ? images : new String[0];
		this.ytLink = ytLink;
		this.owner = owner;
		this.creators = creators;
		this.type = type;
		this.conditions = conditions != null ? conditions : new java.util.HashMap<>();
		this.hasX = hasX;
		this.hasY = hasY;
		this.hasZ = hasZ;
	}

	public String getUuid() {
		return uuid;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getDescriptionMd() {
		return descriptionMd;
	}

	public String getAuthor() {
		return author;
	}

	public int getDownloads() {
		return downloads;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public String[] getTags() {
		return tags;
	}

	public String[] getVersions() {
		return versions;
	}

	public String[] getImages() {
		return images;
	}

	public String getYtLink() {
		return ytLink;
	}

	public boolean isOwner() {
		return owner;
	}

	public String getCreators() {
		return creators;
	}

	public String getType() {
		return type;
	}

	public java.util.Map<String, java.util.List<String>> getConditions() {
		return conditions;
	}

	public boolean hasX() {
		return hasX;
	}

	public boolean hasY() {
		return hasY;
	}

	public boolean hasZ() {
		return hasZ;
	}
}
