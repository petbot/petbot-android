package com.atos.petbot;

import java.io.IOException;

import org.json.JSONException;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class AccountService extends Service {
	
	private AccountAuthenticator authenticator;
	
	@Override
    public void onCreate() {
        authenticator = new AccountAuthenticator(this);
    }
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return authenticator.getIBinder();
	}
	
	public class AccountAuthenticator extends AbstractAccountAuthenticator {  
		  
	    public AccountAuthenticator(Context context) {  
	        super(context);  
	        // TODO Auto-generated constructor stub  
	    }  
	  
	    @Override  
	    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options)  
	            throws NetworkErrorException {  
	        
	    	final Intent intent = new Intent(AccountService.this, LoginActivity.class);
	        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
	        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, accountType);
	        intent.putExtra(AccountManager.KEY_AUTHENTICATOR_TYPES, authTokenType);
	        
	        final Bundle bundle = new Bundle();
            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
            return bundle; 
	    }  
	  
	    @Override  
	    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {  
	        // TODO Auto-generated method stub  
	        return null;  
	    }  
	  
	    @Override  
	    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {  
	        // TODO Auto-generated method stub  
	        return null;  
	    }  
	  
	    @Override  
	    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
	    	
	    	final AccountManager account_manager = AccountManager.get(AccountService.this);
	    	String auth_token = account_manager.peekAuthToken(account, authTokenType);
	    	
	    	if (auth_token.isEmpty()) {
	            final String password = account_manager.getPassword(account);
	            if (password != null) {
	            	auth_token = ServerInfo.login(account.name, password);	           
	            }
	        }
	     
	        // If we get an authToken - we return it
	        if (!auth_token.isEmpty()) {
	            final Bundle result = new Bundle();
	            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
	            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
	            result.putString(AccountManager.KEY_AUTHTOKEN, auth_token);
	            return result;
	        }
	    	
	        return addAccount(response, account.type, authTokenType, null, options);  
	    }  
	  
	    @Override  
	    public String getAuthTokenLabel(String authTokenType) {  
	        // TODO Auto-generated method stub  
	        return null;  
	    }  
	  
	    @Override  
	    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {  
	        // TODO Auto-generated method stub  
	        return null;  
	    }  
	  
	    @Override  
	    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {  
	        // TODO Auto-generated method stub  
	        return null;  
	    }  
	  
	}  
}


