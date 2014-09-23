package com.atos.petbot;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * Created by root on 6/25/14.
 */
public class ServerInfo {
	public final static String url = "https://www.petbot.ca";
	
	public static String login(String email, String password) {
		
		try{
		// encode login information
		JSONObject login = new JSONObject();
		login.put("email", email);
		login.put("password", password);
		String parameters = login.toString();

		URL server = new URL(url + "/login");
		HttpsURLConnection connection = (HttpsURLConnection) server.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("Content-Length", String.valueOf(parameters.length()));

		connection.setDoOutput(true);
		DataOutputStream out_stream = new DataOutputStream(connection.getOutputStream());
		out_stream.writeBytes(parameters);
		out_stream.flush();
		out_stream.close();

		BufferedReader in_stream = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line = null;
		while ((line = in_stream.readLine()) != null) {
			response.append(line);
		}

		JSONObject login_response = new JSONObject(response.toString());
		int response_code = login_response.getJSONObject("meta").getInt("code");
		
		String token = "";
		if(response_code == 200){

			// return authentication token in result intent
			token = login_response.getJSONObject("response").getJSONObject("user").getString("authentication_token");
		}
		
		return token;
		
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}
}
