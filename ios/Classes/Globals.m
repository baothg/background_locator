//
//  Constants.m
//  background_locator
//
//

#import "Globals.h"

@implementation Globals

NSString *const kCallbackDispatcherKey = @"com.thgbao.background_locator.callback_dispatcher";
NSString *const kCallbackKey = @"com.thgbao.background_locator.callback";
NSString *const kInitCallbackKey = @"com.thgbao.background_locator.init_callback";
NSString *const kInitDataCallbackKey = @"com.thgbao.background_locator.init_data";
NSString *const kDisposeCallbackKey = @"com.thgbao.background_locator.dispose_callback";
NSString *const kDistanceFilterKey = @"com.thgbao.background_locator.distance_filter";
NSString *const kChannelId = @"app.thgbao/locator_plugin";
NSString *const kBackgroundChannelId = @"app.thgbao/locator_plugin_background";

NSString *const kMethodServiceInitialized = @"LocatorService.initialized";
NSString *const kMethodPluginInitializeService = @"LocatorPlugin.initializeService";
NSString *const kMethodPluginRegisterLocationUpdate = @"LocatorPlugin.registerLocationUpdate";
NSString *const kMethodPluginUnRegisterLocationUpdate = @"LocatorPlugin.unRegisterLocationUpdate";
NSString *const kMethodPluginIsRegisteredLocationUpdate = @"LocatorPlugin.isRegisterLocationUpdate";
NSString *const kMethodPluginIsServiceRunning = @"LocatorPlugin.isServiceRunning";
NSString *const kMethodPluginUpdateNotification = @"LocatorPlugin.updateNotification";

NSString *const kArgLatitude = @"latitude";
NSString *const kArgLongitude = @"longitude";
NSString *const kArgAccuracy = @"accuracy";
NSString *const kArgAltitude = @"altitude";
NSString *const kArgSpeed = @"speed";
NSString *const kArgSpeedAccuracy = @"speed_accuracy";
NSString *const kArgHeading = @"heading";
NSString *const kArgTime = @"time";
NSString *const kArgCallback = @"callback";
NSString *const kArgInitCallback = @"initCallback";
NSString *const kArgInitDataCallback = @"initDataCallback";
NSString *const kArgDisposeCallback = @"disposeCallback";
NSString *const kArgLocation = @"location";
NSString *const kArgSettings = @"settings";
NSString *const kArgCallbackDispatcher = @"callbackDispatcher";

NSString *const kSettingsAccuracy = @"settings_accuracy";
NSString *const kSettingsDistanceFilter = @"settings_distanceFilter";
NSString *const kSettingsShowsBackgroundLocationIndicator = @"settings_ios_showsBackgroundLocationIndicator";
NSString *const kSettingsStopWithTerminate = @"settings_ios_stopWithTerminate";

NSString *const kBCMSendLocation = @"BCM_SEND_LOCATION";
NSString *const kBCMInit = @"BCM_INIT";
NSString *const kBCMDispose = @"BCM_DISPOSE";

NSString *const kPrefObservingRegion = @"com.thgbao.background_locator.observing_region";
NSString *const kPrefServiceRunning = @"com.thgbao.background_locator.service_running";
NSString *const kPrefStopWithTerminate = @"com.thgbao.background_locator.stop_with_terminate";


@end
