
package com.example.simplepaint;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.simplepaint.PaintView.Files;
import com.example.simplepaint.ics.R;

public class PaintFragment extends Fragment {
    static Integer[] COLORS = new Integer[] {
            0xff000000, // 黒
            0xffffffff, // 白
            0xffff1f37, // 赤
            0xffffa63f, // オレンジ
            0xffffff45, // 黄
            0xffa4c639, // 緑
            0xff5757ff, // 青
            0xffad59eb, // 紫
    };

    PaintView mCanvas;

    ActionBar.OnNavigationListener mNavigationCallback = new ActionBar.OnNavigationListener() {
        @Override
        public boolean onNavigationItemSelected(int itemPosition, long itemId) {
            mCanvas.setPenColor(COLORS[itemPosition].intValue());
            return true;
        }
    };

    public PaintFragment() {
        // nothing to do
    }

    public void setStrokeString(String strokeString) {
        mCanvas.restore(strokeString);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        ActionBar actionBar = getActivity().getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_canvas, container, false);
        mCanvas = (PaintView) view.findViewById(R.id.canvas);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ActionBar actionBar = getActivity().getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

        ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getActivity(),
                0, COLORS) {
            LayoutInflater mInflater = LayoutInflater.from(getContext());

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ImageView view;
                if (convertView == null) {
                    view = (ImageView) mInflater.inflate(R.layout.list_item_color, parent, false);
                } else {
                    view = (ImageView) convertView;
                }
                Integer color = getItem(position);
                view.setBackgroundColor(color.intValue());
                return view;
            }

            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return getView(position, convertView, parent);
            }
        };

        actionBar.setListNavigationCallbacks(adapter, mNavigationCallback);

        if (savedInstanceState != null) {
            String stroke = savedInstanceState.getString(STATE_STROKES);
            if (stroke != null) {
                mCanvas.restore(stroke);
            }
        }
    }

    private static final String STATE_STROKES = "STROKES";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_STROKES, mCanvas.getStrokeString());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity().isFinishing()) {
            if (!saveImage()) {
                Toast.makeText(getActivity(), "画像が保存できませんでした", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu, menu);

        MenuItem bgItem = menu.findItem(R.id.bg_color);
        setBgColorOnMenuClick(bgItem, R.id.black, COLORS[0]);
        setBgColorOnMenuClick(bgItem, R.id.white, COLORS[1]);
        setBgColorOnMenuClick(bgItem, R.id.red, COLORS[2]);
        setBgColorOnMenuClick(bgItem, R.id.orange, COLORS[3]);
        setBgColorOnMenuClick(bgItem, R.id.yellow, COLORS[4]);
        setBgColorOnMenuClick(bgItem, R.id.green, COLORS[5]);
        setBgColorOnMenuClick(bgItem, R.id.blue, COLORS[6]);
        setBgColorOnMenuClick(bgItem, R.id.purple, COLORS[7]);
    }

    private void setBgColorOnMenuClick(final MenuItem item, int id, final int color) {
        View actionView = item.getActionView();
        actionView.findViewById(id).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCanvas.setBackgroundColor(color);
                item.collapseActionView();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // GalleryActivity へ戻る。
                Intent intent = new Intent(getActivity(), GalleryActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            case R.id.share:
                shareImage();
                return true;
            case R.id.clear:
                clearCanvas();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean saveImage() {
        Files saved = mCanvas.save();
        return saved != null;
    }

    private void shareImage() {
        final Uri imageUri = mCanvas.saveImageAsPng();
        if (imageUri == null) {
            Toast.makeText(getActivity(), "ファイルが作成できません", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void clearCanvas() {
        mCanvas.clearCanvas();
    }
}
