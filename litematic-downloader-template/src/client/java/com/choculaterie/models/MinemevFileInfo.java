package com.choculaterie.models;

public class MinemevFileInfo {
	private final int id;
	private final String defaultFileName;
	private final String downloadUrl;
	private final long fileSize;
	private final String[] versions;
	private final int downloads;
	private final String fileType;
	private final boolean isVerified;

	public MinemevFileInfo(int id, String defaultFileName, String downloadUrl, long fileSize,
						  String[] versions, int downloads, String fileType, boolean isVerified) {
		this.id = id;
		this.defaultFileName = defaultFileName;
		this.downloadUrl = downloadUrl;
		this.fileSize = fileSize;
		this.versions = versions != null ? versions : new String[0];
		this.downloads = downloads;
		this.fileType = fileType;
		this.isVerified = isVerified;
	}

	public int getId() {
		return id;
	}

	public String getDefaultFileName() {
		return defaultFileName;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public long getFileSize() {
		return fileSize;
	}

	public String[] getVersions() {
		return versions;
	}

	public int getDownloads() {
		return downloads;
	}

	public String getFileType() {
		return fileType;
	}

	public boolean isVerified() {
		return isVerified;
	}
}
