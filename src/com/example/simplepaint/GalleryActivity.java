
package com.example.simplepaint;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;

import com.example.simplepaint.ics.R;

public class GalleryActivity extends Activity {
    GridView mImages;

    MultiChoiceModeListener mActionModeCalback = new MultiChoiceModeListener() {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.gallery_action, menu);
            mode.setTitle("対象を選択");
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.delete:
                    deleteCheckedImages();
                    updateImages();
                    // アクションモード終了
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gallery);
        mImages = (GridView) findViewById(R.id.images);

        // ロングタップで複数選択モード(アクションモード)に入るように指定
        mImages.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        mImages.setMultiChoiceModeListener(mActionModeCalback);

        mImages.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> container, View view, int position, long id) {
                File strokeFile = (File) container.getItemAtPosition(position);
                PaintActivity.startActivity(GalleryActivity.this, strokeFile);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateImages();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.gallery_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add:
                Intent intent = new Intent(this, PaintActivity.class);
                startActivity(intent);

                return true;
            case R.id.delete:
                if (0 < mImages.getAdapter().getCount()) {
                    mImages.startActionMode(mActionModeCalback);
                    // 一つ選択しないと連続選択のモードにならないので
                    mImages.setItemChecked(0, true);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateImages() {
        File[] strokeFiles = PaintView.listStrokeFiles(getApplicationContext());
        ListAdapter adapter = new ArrayAdapter<File>(this, 0, strokeFiles) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewGroup gridItemView;
                ImageView view;

                if (convertView == null) {
                    gridItemView = new PaintView.CheckableLayout(GalleryActivity.this);
                    gridItemView.setLayoutParams(new GridView.LayoutParams(
                            GridView.LayoutParams.WRAP_CONTENT,
                            GridView.LayoutParams.WRAP_CONTENT));

                    view = new ImageView(GalleryActivity.this);
                    view.setLayoutParams(new ViewGroup.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT));
                    view.setPadding(12, 12, 12, 12);
                    gridItemView.addView(view);
                    gridItemView.setTag(view);
                } else {
                    gridItemView = (ViewGroup) convertView;
                    view = (ImageView) gridItemView.getTag();
                }

                File strokeFile = getItem(position);
                Bitmap thumbnail = PaintView.getThumbnailBitmap(strokeFile);
                if (thumbnail != null) {
                    view.setImageBitmap(thumbnail);
                }

                return gridItemView;
            }
        };

        mImages.setAdapter(adapter);
    }

    private void deleteCheckedImages() {
        SparseBooleanArray checkedItemPositions = mImages.getCheckedItemPositions();
        @SuppressWarnings("unchecked")
        ArrayAdapter<File> adapter = (ArrayAdapter<File>) mImages.getAdapter();
        for (int position = 0; position < adapter.getCount(); position++) {
            boolean checked = checkedItemPositions.get(position);
            if (checked) {
                File strokeFile = adapter.getItem(position);
                PaintView.deleteImage(strokeFile);
            }
        }
    }
}
