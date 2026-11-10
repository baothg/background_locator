//
//  DisposePluggable.m
//  background_locator
//
//

#import "DisposePluggable.h"
#import "PreferencesManager.h"
#import "Globals.h"
#import "BackgroundLocatorPlugin.h"

@implementation DisposePluggable

- (void)onServiceDispose {
    NSDictionary *map = @{
                     kArgDisposeCallback : @([PreferencesManager getCallbackHandle:kDisposeCallbackKey])
                     };
    [[BackgroundLocatorPlugin getInstance] invokeMethod:kBCMDispose arguments:map];
}

- (void)onServiceStart:(NSDictionary *)initialDataDictionary {
    // nop
}

- (void)setCallback:(int64_t)callbackHandle {
    [PreferencesManager setCallbackHandle:callbackHandle key:kDisposeCallbackKey];
}

@end
