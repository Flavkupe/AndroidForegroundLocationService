# AndroidForegroundLocationService
I created this repo to share the implementation for an Android Foreground Service that can be used to poll for a user's location while the screen is locked.

This service can be built and used without much trouble as a Unity plugin. I used this with great success for my location-based game, and since it was difficult to figure out the details (this was my first time creating such a service), I thought it'd be nice to share it. There isn't too much info online on this sort of thing.

The current implementation has a lot of hardcoded stuff, but I may create a version that is a bit easier to adapt to your own projects if needed.

**DISCLAIMER:**
I'm not an Android developer by trade, as this was done as part of a hobby/passion project, so some of the specifics here might not follow best practices.

## What is this?
This is a Foreground Service implementation which allows you to collect a set of geo locations (latitude/longitude coordinates) for an Android app while the screen is locked. Unlike a Background Service, Foreground Services have fewer restrictions for collecting location, with the caveat that they must notify the user that they are running, and must request specific permissions to be used.

This is helpful for apps like running trainers that the user might want to track their location while the device is locked in their pocket. What this will do is to collect a number of coordinates (and their timestamps) which can be queried by the app that launches this service to process the coordinates that were collected while the phone was locked.

This is ideal for Unity games/apps that might wish to perform some more complex processing steps on the coordinates. As such, I kept the outputs and processing of this service as simple as possible in order to make it broadly usable, rather than serving a specific purpose.

I used this for my own Unity game which had a system where the user could discover fictional locations while travelling (even if the phone is locked).

## Using this for Unity

First, clone this repo and open in in Android Studio.

Replace the City.jpg icon in `foregroundlocationservice/src/main/res/drawable/city.jpg` with something representing your own game. You'll need to then build the project (`Build -> Make Project`) and move the generated file in `foregroundlocationservice\build\outputs\aar` to the `Assets/Plugins/Android` folder in your Unity project (create that folder if it does not exist).

### Unity Gradle changes required
You'll also need to create a [custom gradleTemplate and mainTemplate](https://docs.unity3d.com/6000.0/Documentation/Manual/gradle-templates.html). In those, you'll want to add the following:

`gradleTemplate.properties:`
```
android.useAndroidX=true
android.enableJetifier=true
```

`mainTemplate.gradle:`
Add the following under `dependencies`:
```
    implementation("androidx.core:core:1.5.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
```

(Note: I'm not 100% sure how much these are necessary, and you may be able to make it work without these after making some changes to this package).

### Using this in the code
I've shared the following `AndroidForegroundLocationProvider` class which can be used with this plugin.

NOTE: There might be improvements to be made here. If you know of any, let me know.

```
[Serializable]
public class BackgroundCoordinates
{
    public double Latitude;
    public double Longitude;

    /// <summary>
    /// Unix UTC timestamp in milliseconds.
    /// </summary>
    public long Timestamp;

    public override string ToString()
    {
        return $"[{Longitude},{Latitude},{Timestamp}]";
    }
}

[Serializable]
public class BackgroundCoordinateList
{
    [SerializeField]
    public List<BackgroundCoordinates> Coordinates = new();
}

public class AndroidForegroundLocationProvider : MonoBehaviour
{
    private AndroidJavaObject _activityContext = null;
    private AndroidJavaObject _appContext = null;

    const string SERVICE_CLASS = "com.flavkupe.foregroundlocationservice.LocationService";

    void Start()
    {
        if (Application.platform != RuntimePlatform.Android)
        {
            return;
        }

        using (AndroidJavaClass activityClass = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            _activityContext = activityClass.GetStatic<AndroidJavaObject>("currentActivity");
            _appContext = _activityContext.Call<AndroidJavaObject>("getApplicationContext");
        }

        if (_activityContext == null)
        {
            Debug.LogError("Failed to get activity context");
        }

        if (_appContext == null)
        {
            Debug.LogError("Failed to get app context");
        }

        StartService();
    }

    public bool StartService()
    {
        if (Application.platform != RuntimePlatform.Android)
        {
            return false;
        }

        try
        {
            using AndroidJavaClass serviceClass = new AndroidJavaClass(SERVICE_CLASS);
            Debug.Log("Starting service");
            serviceClass.CallStatic("startLocationService", _activityContext);
            return true;
        }
        catch (System.Exception e)
        {
            Debug.LogError("Failed to call Java method: " + e.Message);
            return false;
        }   
    }

    public bool StartPolling()
    {
        if (Application.platform != RuntimePlatform.Android)
        {
            return false;
        }

        using AndroidJavaClass serviceClass = new AndroidJavaClass(SERVICE_CLASS);
        return serviceClass.CallStatic<bool>("startPollingLocation");
    }

    public bool StopPolling()
    {
        if (Application.platform != RuntimePlatform.Android)
        {
            return false;
        }

        using AndroidJavaClass serviceClass = new AndroidJavaClass(SERVICE_CLASS);
        return serviceClass.CallStatic<bool>("stopPollingLocation");
    }

    public BackgroundCoordinateList GetCoordinates()
    {
        if (Application.platform != RuntimePlatform.Android)
        {
            return new BackgroundCoordinateList();
        }

        Debug.Log("Getting coordinates");
        using AndroidJavaClass serviceClass = new AndroidJavaClass(SERVICE_CLASS);
        var locationsJSON = serviceClass.CallStatic<string>("getLocations");

        Debug.Log(locationsJSON);

        var coordsList = JsonUtility.FromJson<BackgroundCoordinateList>(locationsJSON);
        if (coordsList == null || coordsList.Coordinates == null)
        {
            Debug.Log("Unable to deserialize coords!");
            return new BackgroundCoordinateList(); ;
        }

        return coordsList;
    }
}
```

You'll want to call `StartPolling` right after the application is paused (though you may want to also check whether the screen was locked). For example:

`SomeMonoBehaviourYouOwn.cs`
```
public class SomeMonoBehaviourYouOwn : MonoBehaviour
{
    // set this from the inspector or find it from `Start` etc
    [SerializeField]
    private AndroidForegroundLocationProvider _provider;

    

    void OnApplicationPause(bool pause)
    {
        if (Application.platform != RuntimePlatform.Android)
        {
            return;
        }

        if (pause)
        {
            _provider.StopPolling();
            var coords = _provider.GetCoordinates();
            ProcessCoords(coords);
        }
        else
        {
            _provider.StartPolling();
        }
    }

    void ProcessCoords(BackgroundCoordinateList coordList)
    {
       // TODO: something with the coordinates
    }
}
```

## Troubleshooting
This readme is probably missing A LOT of detail... I got this running mostly ad-hoc for my own use. If you can't get things working but are interested in using this, let me know and I'd be happy to improve on this a bit.
