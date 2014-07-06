if [ -z "$ANDROID_SDK" ]; then
	echo Please set ANDROID_SDK correctly
fi
if [ -z "$ANDROID_NDK" ]; then
	echo Please set ANDROID_NDK correctly
fi

export PATH=$PATH:${ANDROID_NDK} 

git submodule update --init
cd ijkplayer
./init-android.sh
cd android
./compile-ffmpeg.sh all
./compile-ijk.sh
