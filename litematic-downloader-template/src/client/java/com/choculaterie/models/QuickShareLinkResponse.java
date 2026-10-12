package com.choculaterie.models;

import org.jspecify.annotations.NonNull;

public record QuickShareLinkResponse(String shortUrl) {


	@Override
	public @NonNull String toString() {
		return "QuickShareLinkResponse{shortUrl='" + shortUrl + "'}";
	}
}

