package com.choculaterie.plugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

final class PluginJson {
	static final Gson GSON = new Gson();
	static final Type FIELD_MAP = new TypeToken<Map<String, PluginManifest.Field>>() {
	}.getType();

	private PluginJson() {
	}
}
