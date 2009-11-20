#include <Carbon/Carbon.h>
#include <JavaVM/jni.h>
#ifdef CARBON
#include <AEDataModel.h>
#endif
#include "org_gudy_azureus2_platform_macosx_access_jnilib_OSXAccess.h"
#include <IOKit/IOBSD.h>
#include <sys/mount.h>
#include <wchar.h>
#import <IOKit/storage/IOMedia.h>
#import <IOKit/storage/IOCDMedia.h>
#import <IOKit/storage/IODVDMedia.h>
#include <IOKit/storage/IOBlockStorageDevice.h>

#include "IONotification.h"

#define VERSION "1.08"

#define assertNot0(a) if (a == 0) { fprintf(stderr, "%s is 0\n", #a); return; }
void fillServiceInfo(io_service_t service, JNIEnv *env, jobject hashMap, jmethodID methPut);

extern "C" {
	void notify(const char *mount, io_service_t service, struct statfs *fs, bool added);
	void notifyURL(const char *url);
}

/**
* AEDesc code from SWT, os_structs.c
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 */
typedef struct AEDesc_FID_CACHE {
	int cached;
	jclass clazz;
	jfieldID descriptorType, dataHandle;
} AEDesc_FID_CACHE;

AEDesc_FID_CACHE AEDescFc;

static jclass gCallBackClass = 0;
static jobject gCallBackObj = 0;

static JavaVM *gjvm = 0;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	gjvm = vm;

	return JNI_VERSION_1_4;
}

#ifdef CARBON

void cacheAEDescFields(JNIEnv *env, jobject lpObject) {
	if (AEDescFc.cached)
		return;
	AEDescFc.clazz = env->GetObjectClass(lpObject);
	AEDescFc.descriptorType = env->GetFieldID(AEDescFc.clazz, "descriptorType", "I");
	AEDescFc.dataHandle = env->GetFieldID(AEDescFc.clazz, "dataHandle", "I");
	AEDescFc.cached = 1;
}

AEDesc *getAEDescFields(JNIEnv *env, jobject lpObject, AEDesc *lpStruct) {
	if (!AEDescFc.cached)
		cacheAEDescFields(env, lpObject);
	lpStruct->descriptorType = (DescType) env->GetIntField(lpObject, AEDescFc.descriptorType);
	lpStruct->dataHandle = (AEDataStorage) env->GetIntField(lpObject, AEDescFc.dataHandle);
	return lpStruct;
}

void setAEDescFields(JNIEnv *env, jobject lpObject, AEDesc *lpStruct) {
	if (!AEDescFc.cached)
		cacheAEDescFields(env, lpObject);
	env->SetIntField(lpObject, AEDescFc.descriptorType, (jint) lpStruct->descriptorType);
#ifndef __LP64__
	env->SetIntField(lpObject, AEDescFc.dataHandle, (jint) lpStruct->dataHandle);
#endif
}

JNIEXPORT jint JNICALL Java_org_gudy_azureus2_platform_macosx_access_jnilib_OSXAccess_AEGetParamDesc(JNIEnv *env,
																																																		 jclass that, jint theAppleEvent, jint theAEKeyword, jint desiredType, jobject result) {
	AEDesc _result, *lpresult = NULL;
	
	jint rc = 0;
	
	if (result)
		if ((lpresult = getAEDescFields(env, result, &_result)) == NULL)
			goto fail;
	
	rc = (jint) AEGetParamDesc((const AppleEvent *) theAppleEvent, (AEKeyword) theAEKeyword, (DescType) desiredType,
														 (AEDescList *) lpresult);
	
fail: if (result && lpresult)
		setAEDescFields(env, result, lpresult);
	
	return rc;
}
#endif

JNIEXPORT jstring JNICALL
Java_org_gudy_azureus2_platform_macosx_access_jnilib_OSXAccess_getVersion(
																																					JNIEnv *env, jclass cla) {
	jstring result = env->NewStringUTF((char *) VERSION);
	
	return (result);
}

JNIEXPORT jstring JNICALL
Java_org_gudy_azureus2_platform_macosx_access_jnilib_OSXAccess_getDocDir(
																																				 JNIEnv *env, jclass cla) {
	CFURLRef docURL;
	CFStringRef docPath;
	FSRef fsRef;
	OSErr err = FSFindFolder(kUserDomain, kDocumentsFolderType,
													 kDontCreateFolder, &fsRef);
	
	jstring result = 0;
	
	if (err == noErr) {
		if ((docURL = CFURLCreateFromFSRef(kCFAllocatorSystemDefault, &fsRef))) {
			docPath = CFURLCopyFileSystemPath(docURL, kCFURLPOSIXPathStyle);
			
			if (docPath) {
				// convert to unicode
				CFIndex strLen = CFStringGetLength(docPath);
				UniChar uniStr[strLen];
				CFRange strRange;
				strRange.location = 0;
				strRange.length = strLen;
				CFStringGetCharacters(docPath, strRange, uniStr);
				
				result = env->NewString((jchar*) uniStr, (jsize) strLen);
				
				CFRelease(docPath);
				
				return result;
			}
			CFRelease(docURL);
		}
	}
	return result;
}

JNIEXPORT void JNICALL
Java_org_gudy_azureus2_platform_macosx_access_jnilib_OSXAccess_memmove(
																																			 JNIEnv *env, jclass cla, jbyteArray dest, jint src, jint count) {
	jbyte *dest1;
	
	if (dest) {
		dest1 = env->GetByteArrayElements(dest, NULL);
		memmove((void *) dest1, (void *) src, count);
		env->ReleaseByteArrayElements(dest, dest1, 0);
	}
}

JNIEXPORT void JNICALL
Java_org_gudy_azureus2_platform_macosx_access_jnilib_OSXAccess_initializeDriveDetection
(JNIEnv *env, jclass cla, jobject listener)
{
	// OSXDriveDetectListener
	jclass callback_class = env->GetObjectClass(listener);
	gCallBackClass = (jclass) env->NewGlobalRef(callback_class);
	gCallBackObj = (jobject) env->NewGlobalRef(listener);
	
	IONotification *mountNotification = [IONotification alloc];
	[mountNotification setup];
}

jstring wchar2jstring(JNIEnv* env, const wchar_t* s) {
	jstring result = 0;
	size_t len = wcslen(s);
	size_t sz = wcstombs(0, s, len);
	char c[sz + 1];
	wcstombs(c, s, len);
	c[sz] = '\0';
	result = env->NewStringUTF(c);
	return result;
}

jstring char2jstring(JNIEnv* env, const char *str) {
	return (jstring) env->NewStringUTF(str);
}

jstring CFString2jstring(JNIEnv *env, CFStringRef cfstr) {
	int len = CFStringGetLength(cfstr) * 2 + 1;
	char s[len];
	CFStringGetCString(cfstr, s, len, kCFStringEncodingUTF8);
	return env->NewStringUTF(s);
}

jstring NSString2jstring(JNIEnv *env, NSString *s) {
	if (s == NULL) {
		return 0;
	}
	const char *c = [s UTF8String];
	return env->NewStringUTF(c);
}

jobject createLong(JNIEnv *env, jlong l) {
	jclass clsLong = env->FindClass("java/lang/Long");
	jmethodID longInit = env->GetMethodID(clsLong, "<init>", "(J)V");
	
	jobject o = env->NewObject(clsLong, longInit, l);
	return o;
}

#define IOOBJECTRELEASE(x) if ((x)) IOObjectRelease((x)); (x) = NULL;

io_object_t IOKitObjectFindParentOfClass(io_object_t inService, io_name_t inClassName) {
	io_object_t rval = NULL;
	io_iterator_t iter = NULL;
	io_object_t service = NULL;
	kern_return_t kr;
	
	if (!inService || !inClassName) {
		return NULL;
	}
	
	kr = IORegistryEntryCreateIterator(inService, kIOServicePlane, kIORegistryIterateRecursively
																		 | kIORegistryIterateParents, &iter);
	if (kr != KERN_SUCCESS) {
		goto IORegistryEntryCreateIterator_FAILED;
	}
	
	if (!IOIteratorIsValid(iter)) {
		IOIteratorReset(iter);
	}
	
	while ((service = IOIteratorNext(iter))) {
		if (IOObjectConformsTo(service, inClassName)) {
			rval = service;
			break;
		}
		
		IOOBJECTRELEASE(service);
	}
	
	IOOBJECTRELEASE(iter);
	
IORegistryEntryCreateIterator_FAILED: return rval;
}

void notify(const char *mount, io_service_t service, struct statfs *fs, bool added) {
	
	assertNot0(gCallBackClass);
	assertNot0(gjvm);
	
	JNIEnv* env = NULL;
	gjvm->AttachCurrentThread((void **) &env, NULL);
	assertNot0(env);
	
	jmethodID meth;
	if (added) {
		meth = env->GetMethodID(gCallBackClass, "driveDetected", "(Ljava/io/File;Ljava/util/Map;)V");
	} else {
		meth = env->GetMethodID(gCallBackClass, "driveRemoved", "(Ljava/io/File;Ljava/util/Map;)V");
	}
	
	assertNot0(meth);
	
	jclass clsHashMap = env->FindClass("java/util/HashMap");
	assertNot0(clsHashMap);
	jmethodID constHashMap = env->GetMethodID(clsHashMap, "<init>", "()V");
	assertNot0(constHashMap);
	jmethodID methPut = env->GetMethodID(clsHashMap, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
	assertNot0(methPut);
	
	jclass clsFile = env->FindClass("java/io/File");
	assertNot0(clsFile);
	jmethodID constFile = env->GetMethodID(clsFile, "<init>", "(Ljava/lang/String;)V");
	assertNot0(constFile);
	
	jobject file = 0;
	if (mount) {
		file = env->NewObject(clsFile, constFile, char2jstring(env, mount));
		assertNot0(file);
	}
	
	jobject hashMap = env->NewObject(clsHashMap, constHashMap, "");
	assertNot0(hashMap);
	
	NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
	
	if (fs) {
		/**
		NSString *path = [[NSString alloc] initWithUTF8String:fs->f_mntonname];
		 NSWorkspace *ws = [NSWorkspace sharedWorkspace];
		 BOOL removable;
		 BOOL writable;
		 BOOL unmountable;
		 NSString *description;
		 NSString *fileSystemType;
		 BOOL gotInfo = [ws getFileSystemInfoForPath:path isRemovable:&removable isWritable:&writable isUnmountable:&unmountable description:&description type:&fileSystemType];
		 if (gotInfo) {
			 if (description) {
				 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "description"), NSString2jstring(env, description));
			 }
			 if (fileSystemType) {
				 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "fileSystemType"), NSString2jstring(env,
																																																			 fileSystemType));
			 }
			 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "removable"), createLong(env, (jlong) removable));
			 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "writable"), createLong(env, (jlong) writable));
			 env->CallObjectMethod(hashMap, methPut, char2jstring(env, "unmountable"), createLong(env, (jlong) unmountable));
		 }
		 
		 [path release];
		 **/
		
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "mntfromname"), char2jstring(env, fs->f_mntfromname));
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "mntonname"), char2jstring(env, fs->f_mntonname));
	}

	if (service) {
		fillServiceInfo(service, env, hashMap, methPut);
	}
	
	env->CallVoidMethod(gCallBackObj, meth, file, hashMap);

	[pool release];
}

void addDictionaryToHashMap(CFDictionaryRef dict, JNIEnv *env, jobject hashMap, jmethodID methPut) {
	CFIndex		count;
	CFIndex 		i;
	const void * *	keys;
	boolean_t		new_is_linklocal = TRUE;
	CFStringRef		new_primary = NULL;
	unsigned int 	primary_index = 0;
	const void * *	values;
	
	count = CFDictionaryGetCount(dict);
	if (count == 0) {
		return;
	}
	
	keys   = (const void * *)malloc(sizeof(void *) * count);
	values = (const void * *)malloc(sizeof(void *) * count);
	
	if (keys == NULL || values == NULL) {
		return;
	}
	
	CFDictionaryGetKeysAndValues(dict, keys, values);
	
	for (i = 0; i < count; i++) {
		//fprintf(stderr, "%d. ", i);
		CFStringRef cfstr = (CFStringRef) keys[i];
		int len = CFStringGetLength(cfstr) * 2 + 1;
		char key[len];
		CFStringGetCString(cfstr, key, len, kCFStringEncodingUTF8);
		//fprintf(stderr, "%s", key);
		
		CFTypeRef cft = (CFTypeRef) values[i];
		CFTypeID tCFTypeID = CFGetTypeID(cft);
		if (tCFTypeID == CFStringGetTypeID()) {
			CFStringRef cfstr = (CFStringRef) cft;
			
			int len = CFStringGetLength(cfstr) * 2 + 1;
			char s[len];
			CFStringGetCString(cfstr, s, len, kCFStringEncodingUTF8);
			//fprintf(stderr, " = %s", s);
			env->CallObjectMethod(hashMap, methPut, char2jstring(env, key), char2jstring(env, s));
		} else if (tCFTypeID == CFBooleanGetTypeID()) {
			//fprintf(stderr, " = %s", ((CFBooleanRef)cft == kCFBooleanTrue) ? "true" : "false");
			BOOL b = (CFBooleanRef)cft == kCFBooleanTrue;
			env->CallObjectMethod(hashMap, methPut, char2jstring(env, key), createLong(env, (jlong) b));
		} else if (tCFTypeID == CFNumberGetTypeID()) {
			long long n;
			double d;
			CFNumberRef cfr = (CFNumberRef)cft;
			CFNumberType cfnt = CFNumberGetType(cfr);
			switch (cfnt) {
				case kCFNumberSInt8Type:
				case kCFNumberSInt16Type:
				case kCFNumberSInt32Type:
				case kCFNumberSInt64Type:
				case kCFNumberCharType:
				case kCFNumberShortType:
				case kCFNumberIntType:
				case kCFNumberLongType:
				case kCFNumberLongLongType:
					CFNumberGetValue(cfr, kCFNumberLongLongType, &n);
					//fprintf(stderr, " = %d", n);
					env->CallObjectMethod(hashMap, methPut, char2jstring(env, key), createLong(env, (jlong) n));
					break;
				case kCFNumberFloat32Type:
				case kCFNumberFloat64Type:
				case kCFNumberFloatType:
				case kCFNumberDoubleType:
					CFNumberGetValue(cfr, kCFNumberDoubleType, &d);
					//fprintf(stderr, " = %f", d);
					break;
				default:
					break;
			}
		} else {
			//fprintf(stderr, " unknown %d", tCFTypeID);
		}
		//fprintf(stderr, "\n");
		}
		free(keys);
		free(values);
}

void fillServiceInfo(io_service_t service, JNIEnv *env, jobject hashMap, jmethodID methPut) {
	io_name_t deviceName;
	kern_return_t kr = IORegistryEntryGetName(service, deviceName);
	if (KERN_SUCCESS == kr) {
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "DeviceName"), char2jstring(env, deviceName));
	}
	
	CFStringRef str_bsd_path = (CFStringRef) IORegistryEntryCreateCFProperty(service, CFSTR(kIOBSDNameKey),
																																					 kCFAllocatorDefault, 0);
	if (str_bsd_path) {
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "BSDName"), CFString2jstring(env, str_bsd_path));
	}
	
	BOOL cd = IOObjectConformsTo(service, kIOCDMediaClass);
	BOOL dvd = IOObjectConformsTo(service, kIODVDMediaClass);
	
	io_service_t ioparent;
	ioparent = IOKitObjectFindParentOfClass(service, kIOMediaClass);
	if (ioparent) {
		jclass clsHashMap = env->FindClass("java/util/HashMap");
		assertNot0(clsHashMap);
		jmethodID constHashMap = env->GetMethodID(clsHashMap, "<init>", "()V");
		assertNot0(constHashMap);
		jobject parentHashMap = env->NewObject(clsHashMap, constHashMap, "");
		assertNot0(parentHashMap);
		
		env->CallObjectMethod(hashMap, methPut, char2jstring(env, "parent"), parentHashMap);
		fillServiceInfo(ioparent, env, parentHashMap, methPut);
		
		cd |= IOObjectConformsTo(ioparent, kIOCDMediaClass);
		dvd |= IOObjectConformsTo(ioparent, kIODVDMediaClass);
		
		IOOBJECTRELEASE(ioparent)
	}
	
	env->CallObjectMethod(hashMap, methPut, char2jstring(env, "isCD"), createLong(env, (jlong) cd));
	env->CallObjectMethod(hashMap, methPut, char2jstring(env, "isDVD"), createLong(env, (jlong) dvd));
	// we can expand this one later if needed
	env->CallObjectMethod(hashMap, methPut, char2jstring(env, "isOptical"), createLong(env, (jlong)(dvd || cd)));
	
	CFMutableDictionaryRef properties = NULL;
	kr = IORegistryEntryCreateCFProperties(service, &properties, kCFAllocatorDefault, 0);
	if (kr == KERN_SUCCESS) {
		addDictionaryToHashMap(properties, env, hashMap, methPut);
		CFRelease(properties);
	}


	io_service_t device;
	io_iterator_t services;
	kr = IORegistryEntryCreateIterator(service, kIOServicePlane,
																			kIORegistryIterateParents | kIORegistryIterateRecursively,
																			&services);

	while ((device = IOIteratorNext(services)))
	{
			if (IOObjectConformsTo(device, kIOBlockStorageDeviceClass))  break;

			IOObjectRelease(device);
	}

	IOObjectRelease(services);

	if (device) {
		kr = IORegistryEntryCreateCFProperties(device, &properties, kCFAllocatorDefault, 0);
		if (kr == KERN_SUCCESS) {
			addDictionaryToHashMap(properties, env, hashMap, methPut);

			CFDictionaryRef        sub;
			sub = (CFDictionaryRef) CFDictionaryGetValue(properties, CFSTR(kIOPropertyDeviceCharacteristicsKey));
			if (sub) {
				addDictionaryToHashMap(sub, env, hashMap, methPut);
			}

			CFRelease(properties);
		}
		IOObjectRelease(device);
	}
	
}

void notifyURL(const char *url) {
	if (url == NULL) {
		return;
	}
	assertNot0(gCallBackClass);
	assertNot0(gjvm);
	
	JNIEnv* env = NULL;
	gjvm->AttachCurrentThread((void **) &env, NULL);
	assertNot0(env);

	jclass clsTorrentOpener = env->FindClass("org/gudy/azureus2/ui/swt/mainwindow/TorrentOpener");
	assertNot0(clsTorrentOpener);
	
	jmethodID methOpenTorrent = env->GetStaticMethodID(clsTorrentOpener, "openTorrent", "(Ljava/lang/String;)V");
	assertNot0(methOpenTorrent);
	
	jstring str = (jstring) env->NewStringUTF(url);
	
	env->CallStaticVoidMethod(clsTorrentOpener, methOpenTorrent, str);
	/*
	jclass clsCocoaUIEnh = env->FindClass("org/gudy/azureus2/ui/swt/osx/CocoaUIEnhancer");
	assertNot0(clsCocoaUIEnh);

	jmethodID methFileOpen = env->GetStaticMethodID(clsCocoaUIEnh, "fileOpen", "([Ljava/lang/String;)V");
	assertNot0(methFileOpen);

	jstring str = (jstring) env->NewStringUTF(url);
	
    jobjectArray ret= (jobjectArray)env->NewObjectArray(1, env->FindClass("java/lang/String"), str);

	env->CallStaticVoidMethod(clsCocoaUIEnh, methFileOpen, ret);
	 */
}
