package com.example.melody.service

import android.content.Context
import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MusicServer(
    private val context: Context,
    private val port: Int
) {

    companion object {
        private const val TAG = "MusicServer"
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    fun start() {
        if (isRunning) {
            Log.w(TAG, "Server is already running")
            return
        }

        try {
            serverSocket = ServerSocket(port)
            isRunning = true

            Log.d(TAG, "Music server started on port $port")

            executor.execute {
                while (isRunning && serverSocket?.isClosed == false) {
                    try {
                        val clientSocket = serverSocket?.accept()
                        if (clientSocket != null && isRunning) {
                            executor.execute {
                                handleClient(clientSocket)
                            }
                        }
                    } catch (e: IOException) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client connection", e)
                        }
                    }
                }
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to start server on port $port", e)
            stop()
        }
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.use { socket ->
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Read request
                val requestBuffer = ByteArray(1024)
                val bytesRead = input.read(requestBuffer)

                if (bytesRead > 0) {
                    val request = String(requestBuffer, 0, bytesRead)
                    Log.d(TAG, "Received request: ${request.take(100)}...")

                    // Send a simple HTTP response
                    val response = """
                        HTTP/1.1 200 OK
                        Content-Type: application/json
                        Access-Control-Allow-Origin: *
                        
                        {"status": "Music server is running", "port": $port}
                    """.trimIndent()

                    output.write(response.toByteArray())
                    output.flush()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error handling client", e)
        }
    }

    fun stop() {
        isRunning = false

        try {
            serverSocket?.close()
            executor.shutdown()
            Log.d(TAG, "Music server stopped")
        } catch (e: IOException) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    fun isRunning(): Boolean = isRunning

    fun getPort(): Int = port
}