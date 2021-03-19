package io.secf4ult.demo;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {

    private String[] tabIds;

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity, String[] tabIds) {
        super(fragmentActivity);
        this.tabIds = tabIds;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return new MyFragment(tabIds[position]);
    }

    @Override
    public int getItemCount() {
        return tabIds.length;
    }

    public String getTabTitle(int position) {
        return tabIds[position];
    }
}
