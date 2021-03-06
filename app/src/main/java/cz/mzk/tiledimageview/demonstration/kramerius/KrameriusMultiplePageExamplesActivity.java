package cz.mzk.tiledimageview.demonstration.kramerius;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.util.ArrayList;

import cz.mzk.tiledimageview.demonstration.ExamplesListActivity;
import cz.mzk.tiledimageview.demonstration.R;
import cz.mzk.tiledimageview.demonstration.kramerius.KrameriusExamplesFactory.MonographExample;

/**
 * @author Martin Řehánek
 */
public class KrameriusMultiplePageExamplesActivity extends ExamplesListActivity {
    private static final String TAG = KrameriusMultiplePageExamplesActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, "Kramerius digital library", "docs with multiple pages (images)", new MyAdapter(this,
                KrameriusExamplesFactory.getTestTopLevelUrls()));
    }

    void startFullscreenPagesActivity(String urlStr, String title, String subtitle) {
        try {
            Intent intent = new Intent(this, PageViewerActivity.class);
            KrameriusObjectPersistentUrl url = KrameriusObjectPersistentUrl.valueOf(urlStr);
            intent.putExtra(PageViewerActivity.EXTRA_PROTOCOL, url.getProtocol());
            intent.putExtra(PageViewerActivity.EXTRA_DOMAIN, url.getDomain());
            intent.putExtra(PageViewerActivity.EXTRA_TOP_LEVEL_PID, url.getPid());
            intent.putExtra(PageViewerActivity.EXTRA_TITLE, title);
            intent.putExtra(PageViewerActivity.EXTRA_SUBTITLE, subtitle);
            startActivity(intent);
        } catch (ParseException e) {
            Log.e(TAG, "error parsing url", e);
            Toast.makeText(this, "error parsing url '" + urlStr + "'", Toast.LENGTH_LONG).show();
        }
    }

    class MyAdapter extends ArrayAdapter<MonographExample> {

        private final Context context;
        private final ArrayList<MonographExample> itemsArrayList;

        public MyAdapter(Context context, ArrayList<MonographExample> itemsArrayList) {
            super(context, R.layout.item_kramerius_example, itemsArrayList);
            this.context = context;
            this.itemsArrayList = itemsArrayList;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.item_kramerius_example, parent, false);
            ((TextView) rowView.findViewById(R.id.label)).setText(itemsArrayList.get(position).getTitle());
            ((TextView) rowView.findViewById(R.id.note)).setText(itemsArrayList.get(position).getNote());
            ((TextView) rowView.findViewById(R.id.src)).setText(itemsArrayList.get(position).getSource());

            rowView.findViewById(R.id.container).setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    MonographExample item = itemsArrayList.get(position);
                    String url = item.getUrl();
                    String title = item.getTitle();
                    String subtitle = item.getSource();
                    startFullscreenPagesActivity(url, title, subtitle);
                }
            });
            rowView.setOnLongClickListener(new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    String url = itemsArrayList.get(position).getUrl();
                    Toast.makeText(KrameriusMultiplePageExamplesActivity.this, url, Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            return rowView;
        }
    }

}
