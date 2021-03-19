# VerticalTabLayout
A TabLayout component with vertical orientation in Android, compatible with `ViewPager2`.

![](https://user-images.githubusercontent.com/12830176/111751022-ef0a4400-88ce-11eb-9f20-b08ff245b7de.mp4)

https://user-images.githubusercontent.com/12830176/111751022-ef0a4400-88ce-11eb-9f20-b08ff245b7de.mp4

## Usage
Add JitPack in your root build.gradle.
```
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
then add the dependency in module build.gradle with intended release tag, e.g `v0.1.0`.
```
dependencies {
  implementation 'com.github.secf4ult:VerticalTabLayout:v0.1.0'
}
```
use it like normal `VerticalTabLayout`.
```
VerticalTabLayout verticalTabLayout = findViewById(R.id.verticalTabLayout);
new VerticalTabLayoutMediator(
  verticalTabLayout,
  viewPager,
  ((tab, position) -> tab.setText(tabTitles[position]))).attach;
```
