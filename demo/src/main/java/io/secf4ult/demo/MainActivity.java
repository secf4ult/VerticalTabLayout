package io.secf4ult.demo;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import io.secf4ult.verticaltablayout.VerticalTabLayout;
import io.secf4ult.verticaltablayout.VerticalTabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private static String[] tabTitles = new String[]{"A", "B", "C"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this, tabTitles);
        viewPager.setAdapter(viewPagerAdapter);

        VerticalTabLayout verticalTabLayout = findViewById(R.id.verticalTabLayout);
        new VerticalTabLayoutMediator(verticalTabLayout, viewPager, ((tab, position) -> tab.setText(viewPagerAdapter.getTabTitle(position)))).attach();
    }
}