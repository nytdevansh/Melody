# Melody - Android Music Player

A modern Android music player application built with Kotlin that scans your device for music files and provides a clean, intuitive interface for music playback.

## Features

### 🎵 Core Functionality
- **Device Music Scanning**: Automatically scans and discovers all music files on your device
- **Music Playback**: Play, pause, and control music playback with a background service
- **Song Management**: Browse your music library with detailed song information
- **Search Functionality**: Find songs by title, artist, or album
- **Background Service**: Continue playing music when the app is minimized

### 🎨 User Interface
- **Clean Material Design**: Modern and intuitive user interface
- **Loading States**: Visual feedback during music scanning and loading
- **Empty State Handling**: Helpful messages when no music is found
- **Responsive Layout**: Optimized for different screen sizes

### 🔧 Technical Features
- **HTTP Server**: Built-in music server for potential remote access
- **Permissions Management**: Proper handling of storage and notification permissions
- **Foreground Service**: Music playback continues in background with notification
- **Memory Efficient**: Optimized music scanning and playback

## Requirements

### Minimum Requirements
- **Android Version**: Android 6.0 (API level 23) or higher
- **Storage**: At least 50MB free space
- **Permissions**: Storage access for music scanning
- **Hardware**: Standard Android device with audio capabilities

### Recommended
- **Android Version**: Android 8.0 (API level 26) or higher for optimal experience
- **RAM**: 2GB or more for smooth performance
- **Storage**: Music files stored on internal or external storage

## Installation

### From Source Code

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/melody.git
   cd melody
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the cloned directory and select it

3. **Build the project**
   ```bash
   ./gradlew clean build
   ```

4. **Run on device or emulator**
   - Connect an Android device or start an emulator
   - Click the "Run" button in Android Studio

### APK Installation
1. Download the latest APK from the releases page
2. Enable "Install from unknown sources" in your device settings
3. Install the APK file

## Permissions

The app requires the following permissions:

### Required Permissions
- **READ_EXTERNAL_STORAGE** (Android < 13): Access music files on device storage
- **READ_MEDIA_AUDIO** (Android 13+): Access audio files with scoped storage
- **POST_NOTIFICATIONS**: Show music playback notifications
- **FOREGROUND_SERVICE**: Run music playback in background
- **INTERNET**: For the built-in HTTP server functionality

### Permission Handling
- Permissions are requested at runtime when needed
- Clear explanations provided for why each permission is needed
- App gracefully handles permission denials

## Architecture

### Project Structure
```
app/src/main/java/com/example/melody/
├── MainActivity.kt                 # Main activity and UI logic
├── adapter/
│   └── SongAdapter.kt             # RecyclerView adapter for song list
├── data/
│   ├── Song.kt                    # Song data class
│   └── MusicRepository.kt         # Music scanning and management
└── service/
    ├── MusicPlayerService.kt      # Background music playback service
    └── MusicServer.kt             # HTTP server for music streaming
```

### Key Components

#### MainActivity
- Handles UI interactions and lifecycle
- Manages permissions and initialization
- Coordinates between repository and service

#### MusicRepository
- Scans device for music files using MediaStore
- Provides song data to the UI
- Implements search and filtering functionality

#### MusicPlayerService
- Background service for music playback
- Manages MediaPlayer instance
- Provides foreground notification during playback

#### MusicServer
- Built-in HTTP server on port 8080
- Enables potential remote access to music library
- Handles client connections and requests

## Configuration

### Server Configuration
The built-in HTTP server runs on port 8080 by default. You can modify this in `MainActivity.kt`:

```kotlin
companion object {
    private const val SERVER_PORT = 8080  // Change this port if needed
}
```

### Notification Configuration
Customize the music playback notification in `MusicPlayerService.kt`:

```kotlin
private fun createNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Your App Name")  // Customize title
        .setContentText(currentSong?.title ?: "Playing music")
        .setSmallIcon(R.drawable.ic_music_note)  // Use your icon
        .build()
}
```

## Usage

### First Launch
1. **Grant Permissions**: Allow storage access when prompted
2. **Initial Scan**: The app will automatically scan your device for music files
3. **Browse Library**: View your music collection in the main list

### Playing Music
1. **Select Song**: Tap any song from the list to start playback
2. **Background Play**: Music continues playing when you minimize the app
3. **Notification Controls**: Use the notification to see what's playing

### Refreshing Library
- Tap the "Refresh Music Library" button to rescan for new music files
- Useful after adding new music to your device

## Troubleshooting

### Common Issues

#### No Music Found
- **Check Permissions**: Ensure storage permissions are granted
- **Verify Music Files**: Confirm you have music files on your device
- **File Formats**: Ensure music files are in supported formats (MP3, MP4, etc.)
- **File Size**: Very small files (< 100KB) are filtered out

#### App Crashes on Startup
- **Update Android**: Ensure your device runs a supported Android version
- **Clear Cache**: Clear app cache and data from device settings
- **Reinstall**: Uninstall and reinstall the app

#### Music Won't Play
- **File Access**: Check if the music file still exists at its location
- **Audio Focus**: Close other audio apps that might be using audio resources
- **Storage Space**: Ensure device has sufficient free storage

#### Server Issues
- **Port Conflict**: Change the server port if 8080 is already in use
- **Network Access**: Check if your device allows the app to use network

### Debug Information
Enable debug logging by checking Android Studio's Logcat for messages tagged with:
- `MainActivity`
- `MusicRepository`
- `MusicPlayerService`
- `MusicServer`

## Development

### Contributing
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Building for Release
```bash
# Build release APK
./gradlew assembleRelease

# Build release AAB for Play Store
./gradlew bundleRelease
```

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions focused and small

## Dependencies

### Core Dependencies
```kotlin
// Android Core
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.recyclerview:recyclerview:1.3.2'

// Kotlin Coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

// Parcelize
implementation 'org.jetbrains.kotlinx:kotlinx-parcelize-runtime:1.9.0'
```

### Permissions in Manifest
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.INTERNET" />
```

## Roadmap

### Planned Features
- [ ] **Playlist Management**: Create and manage custom playlists
- [ ] **Equalizer**: Built-in audio equalizer with presets
- [ ] **Lyrics Support**: Display synchronized lyrics for songs
- [ ] **Theme Customization**: Dark mode and custom color themes
- [ ] **Widget Support**: Home screen widget for quick controls
- [ ] **Cloud Integration**: Support for cloud music services
- [ ] **Advanced Search**: Search by genre, year, and other metadata
- [ ] **Queue Management**: Advanced playback queue with reordering

### Technical Improvements
- [ ] **Database Integration**: Room database for better data persistence
- [ ] **Improved Caching**: Better memory and disk caching strategies
- [ ] **Performance Optimization**: Lazy loading and virtualization
- [ ] **Testing**: Comprehensive unit and integration tests
- [ ] **CI/CD**: Automated build and deployment pipeline

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Android MediaStore API for music file discovery
- Android MediaPlayer for audio playback functionality
- Material Design guidelines for UI/UX inspiration
- Open source community for various code examples and best practices

## Support

For support, feature requests, or bug reports:
- **Issues**: Open an issue on GitHub
- **Email**: yadavdevansh456dev@gmail.com
- **Documentation**: Check this README and code comments

## Collaborate

For colaboration feel free to email me.

---

**Made with ❤️ for music lovers**
