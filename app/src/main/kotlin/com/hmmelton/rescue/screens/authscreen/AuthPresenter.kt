package com.hmmelton.rescue.screens.authscreen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.GraphRequest
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.hmmelton.rescue.App
import com.hmmelton.rescue.data.TokenStore
import com.hmmelton.rescue.http.AccessTokens
import com.hmmelton.rescue.http.LoginRequest
import com.hmmelton.rescue.http.User
import com.hmmelton.rescue.screens.mainscreen.MainActivity
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

/**
 * Created by harrisonmelton on 1/16/18.
 * Presenter for auth screen
 */
class AuthPresenter(private val activity: Activity) : IAuthPresenter {

    private val callbackManager = CallbackManager.Factory.create()

    override fun onCreate() {
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                // Call was successful - fetch additional user info
                makeGraphRequest(result.accessToken.token)
            }

            override fun onError(error: FacebookException?) {
                Timber.e(error?.message)
            }

            override fun onCancel() {
                Timber.d("onCancel")
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    override fun onLoginClick() {
        LoginManager.getInstance().logInWithReadPermissions(activity, listOf("email"))
    }

    /**
     * This function makes a call to Facebook's Graph API to fetch more user info.
     */
    private fun makeGraphRequest(token: String) {
        val request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken()
        ) { `object`, response ->

            // If there was no error, login
            if (response.error == null) {
                login(`object`, token)
            }
        }

        // Add requested fields
        val parameters = Bundle()
        parameters.putString("fields", "id,first_name,last_name,email")
        request.parameters = parameters
        request.executeAsync()
    }

    /**
     * This function logs the user in through the Rescue API.
     */
    private fun login(userJson: JSONObject, token: String) {
        val user = LoginRequest(
                userJson.getString("id"),
                userJson.getString("first_name"),
                userJson.getString("last_name"),
                userJson.getString("email")
        )
        (activity.application as App).service
                .login(token, user)
                .enqueue(loginCallback)
    }

    private val loginCallback = object : Callback<User> {
        override fun onResponse(call: Call<User>, response: Response<User>) {
            response.body()?.let { user ->
                val accessToken =
                    response.headers().get(TokenStore.HEADER_ACCESS_TOKEN)
                            ?: throw IllegalStateException("Access token header missing")
                val refreshToken =
                    response.headers().get(TokenStore.HEADER_REFRESH_TOKEN)
                            ?: throw IllegalStateException("Refresh token header missing")

                // Save Rescue API access tokens
                val userSession = (activity.application as App).userSession
                userSession.tokens = AccessTokens(accessToken, refreshToken)
                userSession.user = user

                // Navigate to main page
                activity.startActivity(Intent(activity, MainActivity::class.java))
                activity.finish()
            }
        }

        override fun onFailure(call: Call<User>, t: Throwable) {
            Timber.e(t, "Login failed")
        }
    }
}