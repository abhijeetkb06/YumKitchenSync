# Yum Kitchen Sync

**Peer-to-Peer Restaurant Order Sync with Zero Configuration**

A demo Android app showcasing [Couchbase Lite's MultipeerReplicator](https://docs.couchbase.com/couchbase-lite/current/android/p2psync-multipeer.html) for automatic device discovery and real-time data sync -- no server, no URLs, no manual setup. Devices on the same WiFi network find each other automatically and sync orders in real time.

Built for **Yum Restaurant** to demonstrate how Couchbase Lite's P2P auto-discovery eliminates the need for server infrastructure in restaurant operations.

---

## What This Demonstrates

- **Automatic Device Discovery** -- Devices find each other via DNS-SD (Bonjour) with zero configuration
- **Real-Time P2P Sync** -- Orders sync instantly across all connected devices using MultipeerReplicator
- **Mesh Network** -- Every device syncs with every other device, no single point of failure
- **TLS Security** -- All peer communication is encrypted with self-signed certificates
- **Offline-First** -- Each device has its own local Couchbase Lite database; sync happens when peers are available

---

## App Roles

The app supports three device roles, each with a tailored UI:

| Role | Description |
|------|-------------|
| **Waiter** | Browse menu, build cart, submit orders from the table |
| **Kitchen** | Real-time Kitchen Display System (KDS) board showing incoming orders with status updates |
| **Manager** | Dashboard with network status, order statistics, peer mesh visualization, and code spotlight |

---

## Architecture

```
com.yumkitchen.sync/
+-- data/
|   +-- model/          # Order, OrderItem, MenuItem, DeviceRole
|   +-- repository/     # OrderRepository, MenuRepository (LiveQuery)
|   +-- CouchbaseManager.java   # Core: DB + MultipeerReplicator setup
|   +-- PeerEventBus.java       # Thread-safe observer for P2P events
+-- ui/
|   +-- roleselection/  # RoleSelectionActivity (launcher)
|   +-- main/           # MainActivity (fragment host + bottom nav)
|   +-- waiter/         # WaiterMenuFragment, MenuItemAdapter, CartAdapter
|   +-- kitchen/        # KitchenDisplayFragment, OrderCardAdapter
|   +-- discovery/      # PeerDiscoveryFragment, PeerMeshView, PeerListAdapter
|   +-- manager/        # ManagerDashboardFragment, OrderSummaryAdapter
+-- util/               # Constants, PermissionHelper, TimeUtils
+-- YumKitchenApp.java  # Application class
```

### Key P2P Components

**CouchbaseManager** is the core singleton that manages:
- Couchbase Lite database initialization
- TLS identity creation (self-signed cert with CLIENT_AUTH + SERVER_AUTH)
- MultipeerReplicator configuration and lifecycle
- Event listeners for peer discovery, replication status, and document sync

```java
// The MultipeerReplicator setup -- this is all it takes
MultipeerReplicatorConfiguration config = new MultipeerReplicatorConfiguration.Builder()
    .setPeerGroupID("com.yumkitchen.sync")
    .setIdentity(identity)
    .setAuthenticator(new MultipeerCertificateAuthenticator((peer, certs) -> true))
    .setCollections(collections)
    .build();

MultipeerReplicator replicator = new MultipeerReplicator(config);
replicator.start();  // That's it -- devices auto-discover and sync
```

**PeerEventBus** distributes P2P events (peer joined, peer left, sync status) to UI components on the main thread.

**PeerMeshView** is a custom Canvas-drawn view that animates the peer mesh network in real time -- center node with pulsing rings, peer bubbles with overshoot animation, and connection lines with sync flash effects.

---

## Tech Stack

| Component | Version |
|-----------|---------|
| **Couchbase Lite Enterprise** | 4.0.3 |
| **Language** | Java |
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 34 (Android 14) |
| **AGP** | 8.7.3 |
| **Gradle** | 8.12 |
| **UI** | Android XML Views + Material Design Components |

---

## Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** (required for Gradle 8.12)
- **3+ physical Android devices** on the same WiFi network (emulators won't work for P2P discovery)
- **Couchbase Lite Enterprise Edition** license (the 4.0.3 EE dependency is pulled from Couchbase Maven)

---

## Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/abhijeetkb06/YumKitchenSync.git
   cd YumKitchenSync
   ```

2. **Verify JDK 17**

   The project requires JDK 17. Check `gradle.properties` and update the path if your JDK 17 is installed elsewhere:
   ```properties
   org.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-17.0.5.jdk/Contents/Home
   ```

3. **Open in Android Studio**

   Open the project root folder in Android Studio. Gradle sync should resolve all dependencies automatically, including the Couchbase Lite EE SDK from the Couchbase Maven repository.

4. **Build**
   ```bash
   ./gradlew assembleDebug
   ```
   The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Running the Demo

### Step 1: Install on Multiple Devices

Install the app on 3+ Android devices connected to the **same WiFi network**.

```bash
# Connect each device via USB/ADB and install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Assign Roles

On each device, launch the app and select a role:
- **Device 1** -- Waiter
- **Device 2** -- Kitchen
- **Device 3** -- Manager

### Step 3: Grant Permissions

The app will request location/nearby devices permissions required for WiFi-based peer discovery. Grant all permissions when prompted.

### Step 4: Watch Auto-Discovery

Once roles are selected, devices will automatically discover each other. Navigate to the **Peers** tab on any device to see:
- Animated mesh visualization showing connected peers
- Live peer list with connection status and roles
- Real-time sync indicators

### Step 5: Place and Track Orders

1. On the **Waiter** device: browse the menu, add items to cart, submit an order
2. On the **Kitchen** device: watch the order appear instantly on the KDS board, tap to update status (New -> Preparing -> Ready)
3. On the **Manager** device: view the dashboard with live order statistics and network health

### Demo Talking Points

- **Zero Configuration** -- No server URLs, no IP addresses, no manual pairing. Devices found each other automatically.
- **Instant Sync** -- Orders appear on kitchen displays in real time, no polling.
- **No Server Required** -- The entire system runs peer-to-peer. No cloud, no middleware.
- **Resilient Mesh** -- Remove a device from the network and add it back. It reconnects and syncs automatically.
- **Secure by Default** -- All P2P communication is TLS-encrypted with certificate-based authentication.

---

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Network communication |
| `ACCESS_NETWORK_STATE` | Detect network connectivity |
| `ACCESS_WIFI_STATE` | WiFi network info for discovery |
| `CHANGE_WIFI_MULTICAST_STATE` | Enable multicast for DNS-SD discovery |
| `NEARBY_WIFI_DEVICES` | Android 13+ WiFi peer discovery |
| `ACCESS_FINE_LOCATION` | Required for WiFi scanning on Android 10-12 |
| `ACCESS_COARSE_LOCATION` | Required for WiFi scanning on Android 10-12 |

---

## Project Structure

```
YumKitchenSync/
+-- app/
|   +-- build.gradle
|   +-- src/main/
|       +-- AndroidManifest.xml
|       +-- java/com/yumkitchen/sync/   # 24 Java source files
|       +-- res/
|           +-- layout/        # 14 XML layouts
|           +-- drawable/      # 8 drawable resources
|           +-- anim/          # 2 animations
|           +-- values/        # colors, strings, dimens, styles
|           +-- menu/          # 2 menu resources
|           +-- mipmap-*/      # App launcher icons
+-- build.gradle               # Root build (AGP 8.7.3)
+-- settings.gradle            # Project settings + Couchbase Maven repo
+-- gradle.properties          # JDK 17 config
+-- gradle/wrapper/            # Gradle 8.12 wrapper
```

---

## Couchbase Lite P2P Resources

- [MultipeerReplicator Documentation](https://docs.couchbase.com/couchbase-lite/current/android/p2psync-multipeer.html)
- [Couchbase Lite Android SDK](https://docs.couchbase.com/couchbase-lite/current/android/quickstart.html)
- [P2P Sync Examples (GitHub)](https://github.com/couchbaselabs/couchbase-lite-peer-to-peer-sync-examples)

---

## License

This is a demo application for Couchbase customer evaluation purposes. Couchbase Lite Enterprise Edition requires a valid license for production use.
