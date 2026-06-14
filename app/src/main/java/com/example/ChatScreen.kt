package com.example

import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val searchQuery by viewModel.currentSearchQuery.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Chat") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }

            // Scroll to bottom when new messages arrive
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            // Hidden background search worker
            if (searchQuery != null) {
                HeadlessWebView(
                    searchQuery = searchQuery!!,
                    onResult = { result ->
                        viewModel.onSearchResult(result)
                    }
                )
            }

            // Input Area
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeadlessWebView(searchQuery: String, onResult: (String) -> Unit) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                layoutParams = ViewGroup.LayoutParams(1, 1) // Hidden
                
                addJavascriptInterface(object : Any() {
                    @android.webkit.JavascriptInterface
                    fun postMessage(message: String) {
                        post { onResult(message) }
                    }
                }, "AndroidInterface")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        val js = """
                            (function() {
                                if (window.__extractionStarted) return;
                                window.__extractionStarted = true;

                                // 1. Click Cookie Consent if present
                                var buttons = document.querySelectorAll('button, div[role="button"]');
                                for(var i=0; i<buttons.length; i++) {
                                    var bt = (buttons[i].textContent || "").toLowerCase();
                                    if(bt === 'accept all' || bt === 'i agree' || bt === 'reject all') {
                                        buttons[i].click();
                                    }
                                }

                                var attempt = 0;
                                var interval = setInterval(function() {
                                    attempt++;
                                    try {
                                        // 2. Click "Generate" button for AI Overview if present
                                        var allElements = document.querySelectorAll('*');
                                        for(var i=0; i<allElements.length; i++) {
                                            if (allElements[i].tagName === 'BUTTON' && allElements[i].innerText === 'Generate') {
                                                allElements[i].click();
                                            }
                                        }

                                        // 3. Look for AI Overview block
                                        var aiFound = false;
                                        for(var i=0; i<allElements.length; i++) {
                                            var text = (allElements[i].innerText || "");
                                            if (text === 'AI Overview' || text === 'Generative AI is experimental' || text.split('\n')[0].trim() === 'AI Overview') {
                                                aiFound = true;
                                                var parent = allElements[i].parentElement;
                                                // Traverse up to find a large enough block containing the answer
                                                for(var k=0; k<8; k++) {
                                                    if(parent && parent.parentElement && parent.parentElement.innerText && parent.parentElement.innerText.length > parent.innerText.length + 30) {
                                                        parent = parent.parentElement;
                                                    } else {
                                                        break;
                                                    }
                                                }
                                                
                                                if (parent && parent.innerText && parent.innerText.length > 80) {
                                                    var lines = parent.innerText.split('\n');
                                                    var result = "";
                                                    for(var j=0; j<lines.length; j++) {
                                                        var l = lines[j].trim();
                                                        // Ignore UI fluff elements
                                                        if(l.length > 0 && l !== 'AI Overview' && l.indexOf('Generative AI is experimental') === -1 && l !== 'Learn more' && l !== 'Show more' && l !== 'Listen' && l !== 'Sign in') {
                                                            result += l + "\n";
                                                        }
                                                    }
                                                    
                                                    if (result.length > 20) {
                                                        clearInterval(interval);
                                                        AndroidInterface.postMessage(result.trim());
                                                        return;
                                                    }
                                                }
                                            }
                                        }
                                        
                                    } catch(e) {}
                                    
                                    if (attempt >= 10) { // 10 seconds max wait
                                        clearInterval(interval);
                                        // Fallbacks to standard snippets
                                        var fallbackSelectors = ['.hgKElc', '.VwiC3b', '.kCrYT', '.s3v9rd', '.LGOjhe'];
                                        for(var s=0; s<fallbackSelectors.length; s++) {
                                            var el = document.querySelector(fallbackSelectors[s]);
                                            if (el && el.innerText && el.innerText.length > 20) {
                                                AndroidInterface.postMessage(el.innerText.trim());
                                                return;
                                            }
                                        }
                                        AndroidInterface.postMessage("Cannot extract AI response for this query.");
                                    }
                                }, 1000);
                            })();
                        """
                        view.evaluateJavascript(js, null)
                    }
                }
            }
        },
        update = { webView ->
            val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
            webView.loadUrl("https://www.google.com/search?q=${encodedQuery}&gl=us&hl=en")
        },
        modifier = Modifier.size(0.dp) // Hidden
    )
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(shape)
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (message.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = contentColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = message.text,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
