package de.audomo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.android.Auth;
import com.dropbox.core.android.AuthActivity;
import com.dropbox.core.oauth.DbxCredential;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DropboxLinkDialog extends DialogFragment {
    private EditText folder;
    private TextView status;
    private boolean waiting;
    private Runnable closeParents;

    static void open(MainActivity screen, CloudConfig old, List<String> selection, Runnable closeParents) {
        Bundle args = new Bundle();
        args.putString("newId", UUID.randomUUID().toString());
        if (old != null) {
            args.putString("oldId", old.id());
            args.putString("oldKey", old.host());
            args.putString("base", old.base());
        }
        args.putStringArrayList("recordings", new ArrayList<>(selection));
        DropboxLinkDialog dialog = new DropboxLinkDialog();
        dialog.closeParents = closeParents;
        dialog.setArguments(args);
        dialog.show(screen.getFragmentManager(), "dropboxLink");
    }

    @Override
    public Dialog onCreateDialog(Bundle state) {
        MainActivity screen = (MainActivity) getActivity();
        AlertDialog.Builder dialog = new AlertDialog.Builder(screen)
                .setTitle("Dropbox").setNegativeButton("Schließen", null);
        if (BuildConfig.DROPBOX_APP_KEY.isEmpty()) {
            return dialog.setMessage(R.string.dropbox_unconfigured).create();
        }
        LinearLayout page = screen.column();
        page.setPadding(screen.dp(20), screen.dp(8), screen.dp(20), screen.dp(8));
        status = screen.text(getString(R.string.dropbox_consent), 15, MainActivity.INK);
        page.addView(status);
        TextView label = screen.text("Zielordner", 14, MainActivity.ACCENT);
        page.addView(label);
        folder = new EditText(screen);
        folder.setId(android.view.View.generateViewId());
        label.setLabelFor(folder.getId());
        folder.setSingleLine();
        folder.setText(state == null ? getArguments().getString("base", "/")
                : state.getString("folder", "/"));
        page.addView(folder);
        waiting = state != null && state.getBoolean("waiting");
        return dialog.setView(page).setPositiveButton(R.string.dropbox_connect, null).create();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (folder == null) return;
        ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> connect());
    }

    private void connect() {
        if (waiting) return;
        try {
            folder.setText(CloudConfig.path(folder.getText().toString()));
        } catch (IllegalArgumentException e) {
            folder.setError(e.getMessage());
            return;
        }
        try {
            AuthActivity.result = null;
            waiting = true;
            Auth.startOAuth2PKCE(getActivity(), BuildConfig.DROPBOX_APP_KEY,
                    DbxRequestConfig.newBuilder("Audomo/" + BuildConfig.VERSION_NAME).build(),
                    List.of("files.metadata.read", "files.content.read", "files.content.write"));
        } catch (RuntimeException e) {
            waiting = false;
            status.setText(R.string.dropbox_launch_failed);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!waiting) return;
        waiting = false;
        DbxCredential credential = Auth.getDbxCredential();
        AuthActivity.result = null;
        if (credential == null || credential.getRefreshToken() == null
                || credential.getRefreshToken().isBlank()
                || !BuildConfig.DROPBOX_APP_KEY.equals(credential.getAppKey())) {
            status.setText(R.string.dropbox_incomplete);
            return;
        }
        MainActivity screen = (MainActivity) getActivity();
        Bundle args = getArguments();
        String base = folder.getText().toString();
        boolean same = BuildConfig.DROPBOX_APP_KEY.equals(args.getString("oldKey"))
                && base.equals(args.getString("base"));
        CloudConfig target = new CloudConfig(args.getString(same ? "oldId" : "newId"),
                "Dropbox", BuildConfig.DROPBOX_APP_KEY, 443, "", base, "");
        List<String> selection = args.containsKey("recordings") ? args.getStringArrayList("recordings")
                : args.containsKey("recording") ? List.of(args.getString("recording")) : List.of();
        dismiss();
        screen.runFiles(() -> {
            target.save(screen, credential.getRefreshToken());
            screen.runOnUiThread(() -> {
                if (screen.isFinishing() || screen.isDestroyed()) return;
                if (closeParents != null) closeParents.run();
                new CloudScreen(screen).afterSetup(selection);
            });
            return "Dropbox verbunden";
        });
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (folder != null) out.putString("folder", folder.getText().toString());
        out.putBoolean("waiting", waiting);
    }
}
