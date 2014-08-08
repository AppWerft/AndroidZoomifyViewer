package cz.mzk.androidzoomifyviewer.examples.kramerius;

import java.text.ParseException;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import cz.mzk.androidzoomifyviewer.examples.R;
import cz.mzk.androidzoomifyviewer.examples.kramerius.KrameriusExamplesFactory.MonographExample;

/**
 * @author Martin Řehánek
 * 
 */
public class KrameriusMultiplePageExamplesActivity extends Activity {
	private static final String TAG = KrameriusMultiplePageExamplesActivity.class.getSimpleName();

	private ListView mListExamples;
	private TextView mTitle;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_examples);
		mTitle = (TextView) findViewById(R.id.title);
		mTitle.setText("Kramerius multiple page examples");
		mListExamples = (ListView) findViewById(R.id.listExamples);
		mListExamples.setAdapter(new MyAdapter(this, KrameriusExamplesFactory.getTestTopLevelUrls()));
	}

	class MyAdapter extends ArrayAdapter<MonographExample> {

		private final Context context;
		private final ArrayList<MonographExample> itemsArrayList;

		public MyAdapter(Context context, ArrayList<MonographExample> itemsArrayList) {
			super(context, R.layout.activity_bak_test_list_row, itemsArrayList);
			this.context = context;
			this.itemsArrayList = itemsArrayList;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View rowView = inflater.inflate(R.layout.activity_bak_test_list_row, parent, false);
			((TextView) rowView.findViewById(R.id.label)).setText(itemsArrayList.get(position).getTitle());
			((TextView) rowView.findViewById(R.id.src)).setText(itemsArrayList.get(position).getSource());
			rowView.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					String url = itemsArrayList.get(position).getUrl();
					startFullscreenPagesActivity(url);
				}
			});
			return rowView;
		}
	}

	void startFullscreenPagesActivity(String urlStr) {
		try {
			Intent intent = new Intent(this, PageViewerActivity.class);
			KrameriusObjectPersistentUrl url = KrameriusObjectPersistentUrl.valueOf(urlStr);
			intent.putExtra(PageViewerActivity.EXTRA_DOMAIN, url.getDomain());
			intent.putExtra(PageViewerActivity.EXTRA_TOP_LEVEL_PID, url.getPid());
			intent.putExtra(PageViewerActivity.EXTRA_PAGE_ID, 0);
			startActivity(intent);
		} catch (ParseException e) {
			Log.e(TAG, "error parsing url '" + urlStr + "'");
			Toast.makeText(this, "error parsing url '" + urlStr + "'", Toast.LENGTH_LONG).show();
		}
	}

}
