# Glimpse Architecture

This document describes the current architecture used by the Android app (`com.android.synclab.glimpse`).

## Layer Order
Presentation -> Domain -> Infra -> Data

Additional cross-cutting layers:
- Base: shared contracts/interfaces for dependency inversion.
- DI: composition root that wires implementations to contracts.
- Utils: stateless helpers used across layers.

## Responsibilities

### Presentation
Location: `app/src/main/kotlin/com/android/synclab/glimpse/presentation`

Contains Android entry points and UI orchestration:
- `MainActivity`
- `BatteryOverlayService`

Presentation must not depend on concrete infra implementations directly. It requests dependencies from DI.

### Domain
Location: `app/src/main/kotlin/com/android/synclab/glimpse/domain`

Contains business use cases:
- `GetConnectedPs4ControllersUseCase`
- `GetPrimaryGamepadBatteryUseCase`

Domain depends on contracts from `base`, not on infra implementations.

### Base
Location: `app/src/main/kotlin/com/android/synclab/glimpse/base/contracts`

Contains interfaces/contracts shared across layers:
- `GamepadRepository`

This allows dependency inversion between Domain/Presentation and Infra.

### Infra
Location: `app/src/main/kotlin/com/android/synclab/glimpse/infra`

Contains platform-bound implementations and gateways:
- `repository/GamepadRepositoryImpl`
- `input/InputDeviceGateway`
- `overlay/OverlayWindowController`
- `notification/MonitoringNotificationController`

Infra implements contracts from `base`.

### Data
Location: `app/src/main/kotlin/com/android/synclab/glimpse/data`

Contains data definitions/state:
- `model/BatteryChargeStatus`
- `model/ControllerInfo`
- `model/GamepadBatterySnapshot`
- `state/MonitoringStateStore`

Data is used by Domain/Infra/Presentation as payload/state models.

### DI
Location: `app/src/main/kotlin/com/android/synclab/glimpse/di`

Composition root:
- `AppContainer`

Responsibilities:
- Create and hold shared dependencies.
- Bind `base` contracts to `infra` implementations.
- Provide dependencies to `MainActivity` and `BatteryOverlayService`.

### Utils
Location: `app/src/main/kotlin/com/android/synclab/glimpse/utils`

Shared stateless utilities:
- `LogCompat` (single log tag: `ds4batmon`)

## Dependency Rules
- Presentation -> Domain/Base/DI (+ Data models for rendering)
- Domain -> Base (+ Data models)
- Infra -> Base/Data/Utils
- Data -> no business orchestration logic
- DI -> may reference all layers to wire dependencies

## Runtime Flow (Summary)
1. Presentation receives user/system events.
2. Presentation calls Domain use cases.
3. Domain uses Base contracts.
4. Infra implementations execute platform operations (InputDevice, overlay, notification).
5. Data models/state are returned and rendered in Presentation.
