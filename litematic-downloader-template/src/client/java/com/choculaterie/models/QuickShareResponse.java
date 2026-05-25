package com.choculaterie.models;

import org.jspecify.annotations.NonNull;

public record QuickShareResponse(String shortUrl) {


	@Override
	public @NonNull String toString() {
		return "QuickShareResponse{shortUrl='" + shortUrl + "'}";
	}
}

