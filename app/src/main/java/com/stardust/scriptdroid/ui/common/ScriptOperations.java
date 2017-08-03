package com.stardust.scriptdroid.ui.common;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.DrawerLayout;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.pio.PFile;
import com.stardust.pio.UncheckedIOException;
import com.stardust.scriptdroid.R;
import com.stardust.scriptdroid.script.ScriptFile;
import com.stardust.scriptdroid.script.Scripts;
import com.stardust.scriptdroid.script.StorageScriptProvider;
import com.stardust.scriptdroid.script.sample.Sample;
import com.stardust.scriptdroid.ui.edit.EditActivity;
import com.stardust.scriptdroid.ui.main.MainActivity;
import com.stardust.scriptdroid.ui.main.script_list.MyScriptListFragment;
import com.stardust.theme.dialog.ThemeColorMaterialDialogBuilder;
import com.stardust.util.UnderuseExecutors;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by Stardust on 2017/7/31.
 */

public class ScriptOperations {

    private Activity mActivity;
    private View mView;
    private ScriptFile mCurrentDirectory;

    public ScriptOperations(Activity activity, View view, ScriptFile currentDirectory) {
        mActivity = activity;
        mView = view;
        mCurrentDirectory = currentDirectory;
    }

    public ScriptOperations(Activity activity, View view) {
        this(activity, view, MyScriptListFragment.getCurrentDirectory());
    }

    public void newScriptFileForScript(final String script) {
        showFileNameInputDialog("", "js")
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull String input) throws Exception {
                        createScriptFile(getCurrentDirectoryPath() + input + ".js", script, false);
                    }
                });
    }

    private String getCurrentDirectoryPath() {
        return getCurrentDirectory().getPath() + "/";
    }

    private ScriptFile getCurrentDirectory() {
        return mCurrentDirectory;
    }

    public void createScriptFile(String path, String script, boolean edit) {
        if (PFile.createIfNotExists(path)) {
            if (script != null) {
                try {
                    PFile.write(path, script);
                } catch (UncheckedIOException e) {
                    Snackbar.make(mView, R.string.text_file_write_fail, Snackbar.LENGTH_LONG).show();
                }
            }
            notifyScriptFileChanged();
            if (edit)
                Scripts.edit(path);
        } else {
            Snackbar.make(mView, R.string.text_create_fail, Snackbar.LENGTH_LONG).show();
        }
    }

    public void newScriptFile() {
        newScriptFileForScript(null);
    }

    public Observable<String> importFile(final String pathFrom) {
        return showFileNameInputDialog(PFile.getNameWithoutExtension(pathFrom), PFile.getExtension(pathFrom))
                .observeOn(Schedulers.io())
                .map(new Function<String, String>() {
                    @Override
                    public String apply(@io.reactivex.annotations.NonNull String s) throws Exception {
                        final String pathTo = getCurrentDirectoryPath() + s + "." + PFile.getExtension(pathFrom);
                        if (PFile.copy(pathFrom, pathTo)) {
                            showMessage(R.string.text_import_succeed);
                        } else {
                            showMessage(R.string.text_import_fail);
                        }
                        notifyScriptFileChanged();
                        return pathTo;
                    }
                });
    }

    public Observable<String> importFile(String prefix, final InputStream inputStream) {
        return showFileNameInputDialog(PFile.getNameWithoutExtension(prefix), "js")
                .observeOn(Schedulers.io())
                .map(new Function<String, String>() {
                    @Override
                    public String apply(@io.reactivex.annotations.NonNull String s) throws Exception {
                        final String pathTo = getCurrentDirectoryPath() + s + ".js";
                        if (PFile.copyStream(inputStream, pathTo)) {
                            showMessage(R.string.text_import_succeed);
                        } else {
                            showMessage(R.string.text_import_fail);
                        }
                        notifyScriptFileChanged();
                        return pathTo;
                    }
                });
    }


    public void newDirectory() {
        showNameInputDialog("", new InputCallback())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(@io.reactivex.annotations.NonNull String path) throws Exception {
                        if (new ScriptFile(getCurrentDirectory(), path).mkdirs()) {
                            showMessage(R.string.text_already_create);
                            notifyScriptFileChanged();
                        } else {
                            showMessage(R.string.text_create_fail);
                        }
                    }
                });
    }

    private void notifyScriptFileChanged() {
        StorageScriptProvider.getDefault().notifyDirectoryChanged(mCurrentDirectory);
    }

    private void showMessage(final int resId) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar.make(mView, resId, Snackbar.LENGTH_SHORT).show();
            }
        });
    }


    private Observable<String> showFileNameInputDialog(String prefix, String ext) {
        return showNameInputDialog(prefix, new InputCallback(ext));
    }

    private Observable<String> showNameInputDialog(String prefix, MaterialDialog.InputCallback textWatcher) {
        final PublishSubject<String> input = PublishSubject.create();
        new ThemeColorMaterialDialogBuilder(mActivity).title(R.string.text_name)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .alwaysCallInputCallback()
                .input(getString(R.string.text_please_input_name), prefix, false, textWatcher)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        input.onNext(dialog.getInputEditText().getText().toString());
                        input.onComplete();
                    }
                })
                .show();
        return input;
    }


    private CharSequence getString(int resId) {
        return mActivity.getString(resId);
    }

    public Observable<String> importSample(Sample sample) {
        try {
            return importFile(sample.name, mActivity.getAssets().open(sample.path));
        } catch (IOException e) {
            e.printStackTrace();
            Snackbar.make(mView, R.string.text_import_fail, Snackbar.LENGTH_SHORT).show();
            return Observable.error(e);
        }
    }

    public Observable<Boolean> rename(final ScriptFile file) {
        String originalName = file.getSimplifiedName();
        return showNameInputDialog(originalName, new InputCallback(file.isDirectory() ? null : PFile.getExtension(file.getName()),
                originalName))
                .map(new Function<String, Boolean>() {
                    @Override
                    public Boolean apply(@io.reactivex.annotations.NonNull String newName) throws Exception {
                        return file.renameTo(newName);
                    }
                });
    }

    private class InputCallback implements MaterialDialog.InputCallback {

        private String mExcluded;
        private boolean mIsFirstTextChanged = true;
        private String mExtension;

        InputCallback(@Nullable String ext, String excluded) {
            mExtension = "." + ext;
            mExcluded = excluded;
        }

        InputCallback(String ext) {
            this(ext, null);
        }

        public InputCallback() {
            this(null);
        }

        @Override
        public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
            if (mIsFirstTextChanged) {
                mIsFirstTextChanged = false;
                return;
            }
            EditText editText = dialog.getInputEditText();
            if (editText == null)
                return;
            int errorResId = 0;
            if (input == null || input.length() == 0) {
                errorResId = R.string.text_name_should_not_be_empty;
            } else if (!input.equals(mExcluded)) {
                if (new File(getCurrentDirectory(), mExtension == null ? input.toString() : input.toString() + mExtension).exists()) {
                    errorResId = R.string.text_file_exists;
                }
            }
            if (errorResId == 0) {
                editText.setError(null);
                dialog.getActionButton(DialogAction.POSITIVE).setEnabled(true);
            } else {
                editText.setError(getString(errorResId));
                dialog.getActionButton(DialogAction.POSITIVE).setEnabled(false);
            }

        }
    }


}
