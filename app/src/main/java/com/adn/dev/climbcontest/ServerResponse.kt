package com.adn.dev.climbcontest

import org.json.JSONObject

sealed class ServerResponse {
    data class Success(val data: JSONObject) : ServerResponse()
    data class Failure(val errorMessage: String) : ServerResponse()
}

