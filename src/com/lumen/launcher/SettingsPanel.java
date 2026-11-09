package com.lumen.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Panneau des réglages (à droite) et ses sous-pages : villes, applis masquées. */
final class SettingsPanel {
    private SettingsPanel() { }


    static void openSettings(final MainActivity a) {
        final int[] before = {a.prefs.source(), a.prefs.followTime() ? 1 : 0, a.prefs.intervalSeconds(), a.prefs.tileStyle(),
                a.prefs.showIcons() ? 1 : 0, a.prefs.tintTiles() ? 1 : 0};
        FrameLayout o = a.openOverlay(new Runnable() {
            @Override public void run() {
                a.clockBox.setVisibility(a.prefs.showClock() ? View.VISIBLE : View.GONE);
                Sounds.get(a).setEnabled(a.prefs.sounds());
                if (!a.prefs.appGlow()) a.glow.fade();
                a.rowRefreshers.clear();
                if (before[3] != a.prefs.tileStyle() || before[4] != (a.prefs.showIcons() ? 1 : 0)
                        || before[5] != (a.prefs.tintTiles() ? 1 : 0)) { a.dirtyRow = true; a.markAppsDirty(); }
            }
        });
        final LinearLayout card = a.glassCard(48);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(a.u(760), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        cl.topMargin = cl.bottomMargin = cl.rightMargin = a.u(48);
        o.addView(card, cl);
        fillSettings(a, card);
    }

    /** Applique tout de suite un changement de fond (source, heure, rythme). */
    static void applyWallChange(final MainActivity a) {
        for (String t : Library.themesFor(a.prefs.source())) a.online.refreshIfNeeded(t, false);
        a.wall.restart();
        a.updateWeatherFx();
        if (a.wall.usingFallback() && Library.themesFor(a.prefs.source()).length > 0)
            a.showNotice("Téléchargement des photos en cours");
    }

    static void fillSettings(final MainActivity a, final LinearLayout card) {
        card.removeAllViews();
        a.rowRefreshers.clear();
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.u(24), a.u(48), a.u(24), a.u(48));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = a.text(48, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Réglages");
        title.setPadding(a.u(32), 0, 0, 0);
        col.addView(title);

        col.addView(a.listRow("Réglages depuis le téléphone", new MainActivity.Value() {
            @Override public String get() { return a.phone.running() ? "Actif" : "QR code"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { PhonePanel.openPhonePanel(a); }
        }), a.rowLp());

        col.addView(a.section("Fond d'écran"));
        LinearLayout first = a.listRow("Source", new MainActivity.Value() {
            @Override public String get() { return Prefs.SRC_NAMES[a.prefs.source()]; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setSource((a.prefs.source() + 1) % Prefs.SRC_NAMES.length); applyWallChange(a); }
        });
        col.addView(first, a.rowLp());
        col.addView(a.listRow("Suivre l'heure de la journée", new MainActivity.Value() {
            @Override public String get() { return a.prefs.followTime() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setFollowTime(!a.prefs.followTime()); applyWallChange(a); }
        }), a.rowLp());
        col.addView(a.listRow("Changer de fond", new MainActivity.Value() {
            @Override public String get() {
                int s = a.prefs.intervalSeconds();
                if (s <= 0) return "Jamais";
                return s < 60 ? "Toutes les " + s + " s" : "Toutes les " + (s / 60) + " min";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.intervalSeconds(), idx = 0;
                for (int i = 0; i < Prefs.INTERVALS.length; i++) if (Prefs.INTERVALS[i] == cur) idx = i;
                a.prefs.setIntervalSeconds(Prefs.INTERVALS[(idx + 1) % Prefs.INTERVALS.length]);
                applyWallChange(a);
            }
        }), a.rowLp());
        col.addView(a.listRow("Choisir les fonds", new MainActivity.Value() {
            @Override public String get() { return "Galerie"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                // fonds vidéo : on choisit sur aperçus avant tout téléchargement
                if (a.prefs.source() == Prefs.SRC_VIDEO) VideoPicker.open(a, Online.SCOPE_FONDS, null);
                else GalleryPanel.openGallery(a);
            }
        }), a.rowLp());
        col.addView(a.listRow("Fonds vidéo", new MainActivity.Value() {
            @Override public String get() { return "Aperçus"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { VideoPicker.open(a, Online.SCOPE_FONDS, null); }
        }), a.rowLp());
        col.addView(a.listRow("Vidéos des ambiances", new MainActivity.Value() {
            @Override public String get() { return "Aperçus"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { VideoPicker.open(a, Online.SCOPE_COSY, null); }
        }), a.rowLp());
        col.addView(a.listRow("Pexels", new MainActivity.Value() {
            @Override public String get() { return a.prefs.pexelsKey().isEmpty() ? "Désactivé" : "Activé · OK pour retirer la clé"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.prefs.pexelsKey().isEmpty()) {
                    a.showNotice("Ajoute ta clé Pexels depuis le téléphone (Réglages depuis le téléphone)");
                } else {
                    a.prefs.setPexelsKey("");
                    a.showNotice("Clé Pexels retirée : Lumen ne contacte plus Pexels");
                }
            }
        }), a.rowLp());
        col.addView(a.listRow("Fond suivant", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.wall.next(); a.showNotice("Fond suivant"); }
        }), a.rowLp());
        col.addView(a.listRow("Masquer ce fond", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                WallItem c = a.wall.current();
                if (c == null) return;
                a.wall.hideCurrent();
                a.showNotice("« " + c.title + " » ne s'affichera plus");
            }
        }), a.rowLp());
        col.addView(a.listRow("Fonds masqués", new MainActivity.Value() {
            @Override public String get() {
                int n = a.prefs.hiddenWalls().size();
                return n == 0 ? "Aucun" : n + " · Tout réafficher";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.prefs.hiddenWalls().isEmpty()) return;
                a.prefs.unhideAllWalls();
                a.wall.refreshList();
                a.showNotice("Tous les fonds sont de nouveau affichés");
            }
        }), a.rowLp());
        col.addView(a.listRow("Thème des vidéos", new MainActivity.Value() {
            @Override public String get() { return Prefs.VT_NAMES[a.prefs.videoTheme()]; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.prefs.setVideoTheme((a.prefs.videoTheme() + 1) % Prefs.VT_NAMES.length);
                a.online.refreshIfNeeded(Online.VIDEO, true);
            }
        }), a.rowLp());
        col.addView(a.listRow("Villes", new MainActivity.Value() {
            @Override public String get() {
                int n = a.prefs.cities().size();
                return n == Online.CITIES.length ? "Toutes" : n + " sur " + Online.CITIES.length;
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { fillCities(a, card); }
        }), a.rowLp());
        col.addView(a.listRow("Espace pour les vidéos", new MainActivity.Value() {
            @Override public String get() { return a.prefs.videoDiskGb() + " Go"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.videoDiskGb(), idx = 0;
                for (int i = 0; i < Prefs.DISK_GB.length; i++) if (Prefs.DISK_GB[i] == cur) idx = i;
                a.prefs.setVideoDiskGb(Prefs.DISK_GB[(idx + 1) % Prefs.DISK_GB.length]);
            }
        }), a.rowLp());
        col.addView(a.listRow("Qualité des vidéos", new MainActivity.Value() {
            @Override public String get() { return a.prefs.video4k() ? "4K" : "HD"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setVideo4k(!a.prefs.video4k()); }
        }), a.rowLp());
        col.addView(a.listRow("Télécharger de nouveaux fonds", new MainActivity.Value() {
            @Override public String get() {
                String[] th = Library.themesFor(a.prefs.source());
                if (th.length == 0) return "—";
                for (String t : th) if (a.online.isBusy(t)) return a.online.status(t);
                return a.online.status(th[0]);
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] th = Library.themesFor(a.prefs.source());
                if (th.length == 0) { a.showNotice("Uniquement pour les sources photo"); return; }
                for (String t : th) a.online.refreshIfNeeded(t, true);
                a.showNotice("Téléchargement lancé");
            }
        }), a.rowLp());

        col.addView(a.section("Mode veille"));
        col.addView(a.listRow("Activer après", new MainActivity.Value() {
            @Override public String get() { int m = a.prefs.ambientMinutes(); return m <= 0 ? "Jamais" : m + " min sans activité"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = a.prefs.ambientMinutes(), idx = 0;
                for (int i = 0; i < Prefs.AMBIENT_MIN.length; i++) if (Prefs.AMBIENT_MIN[i] == cur) idx = i;
                a.prefs.setAmbientMinutes(Prefs.AMBIENT_MIN[(idx + 1) % Prefs.AMBIENT_MIN.length]);
            }
        }), a.rowLp());
        col.addView(a.listRow("Lancer le mode veille", null, new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeOverlay(false); a.enterAmbient(); }
        }), a.rowLp());
        col.addView(a.listRow("Météo", new MainActivity.Value() {
            @Override public String get() { return a.weather.hasCity() ? a.weather.city() : "Choisir une ville"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { CityPanel.openCityInput(a); }
        }), a.rowLp());
        col.addView(a.listRow("Météo dans le paysage", new MainActivity.Value() {
            @Override public String get() {
                if (!a.weather.hasCity()) return "Choisir une ville d'abord";
                return a.prefs.weatherFx() ? "Oui" : "Non";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { if (a.weather.hasCity()) a.prefs.setWeatherFx(!a.prefs.weatherFx()); else CityPanel.openCityInput(a); }
        }), a.rowLp());
        col.addView(a.listRow("Décors de saison", new MainActivity.Value() {
            @Override public String get() { return a.prefs.seasonal() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setSeasonal(!a.prefs.seasonal()); }
        }), a.rowLp());
        col.addView(a.listRow("Économiseur d'écran de la TV", new MainActivity.Value() {
            @Override public String get() { return "Comment l'activer"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.showNotice("Paramètres TV › Système › Économiseur d'écran › Lumen");
            }
        }), a.rowLp());

        col.addView(a.section("Applications"));
        col.addView(a.listRow("Applis par ligne", new MainActivity.Value() {
            @Override public String get() { return String.valueOf(a.prefs.homeCols()); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = a.prefs.homeCols() + 1;
                a.prefs.setHomeCols(n > 7 ? 3 : n);
                a.dirtyRow = true;
            }
        }), a.rowLp());
        col.addView(a.listRow("Nombre de lignes", new MainActivity.Value() {
            @Override public String get() { return String.valueOf(a.prefs.homeRows()); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = a.prefs.homeRows() + 1;
                a.prefs.setHomeRows(n > 3 ? 1 : n);
                a.dirtyRow = true;
            }
        }), a.rowLp());
        col.addView(a.listRow("Vignettes", new MainActivity.Value() {
            @Override public String get() {
                int st = a.prefs.tileStyle();
                return st == Prefs.TILE_WORD ? "Nom de l'appli" : st == Prefs.TILE_BANNER ? "Bannières officielles" : "Icône et nom";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int st = a.prefs.tileStyle();
                a.prefs.setTileStyle(st == Prefs.TILE_WORD ? Prefs.TILE_MONO : st == Prefs.TILE_MONO ? Prefs.TILE_BANNER : Prefs.TILE_WORD);
            }
        }), a.rowLp());
        col.addView(a.listRow("Icônes des applis", new MainActivity.Value() {
            @Override public String get() { return a.prefs.showIcons() ? "Affichées" : "Masquées"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setShowIcons(!a.prefs.showIcons()); }
        }), a.rowLp());
        col.addView(a.listRow("Couleur des vignettes", new MainActivity.Value() {
            @Override public String get() { return a.prefs.tintTiles() ? "Couleur de l'appli" : "Verre neutre"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setTintTiles(!a.prefs.tintTiles()); }
        }), a.rowLp());
        col.addView(a.listRow("Applis masquées", new MainActivity.Value() {
            @Override public String get() { int n = a.prefs.hiddenApps().size(); return n == 0 ? "Aucune" : String.valueOf(n); }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { if (!a.prefs.hiddenApps().isEmpty()) fillHiddenApps(a, card); }
        }), a.rowLp());

        col.addView(a.section("Affichage"));
        col.addView(a.listRow("Reprendre au-dessus des applis", new MainActivity.Value() {
            @Override public String get() {
                if (!a.prefs.topShelf()) return "Non";
                return WatchNext.granted(a) ? "Oui" : "Oui · autorisation à donner";
            }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (a.prefs.topShelf() && !WatchNext.granted(a)) { a.requestWatchNext(); return; }
                a.prefs.setTopShelf(!a.prefs.topShelf());
                a.shelf.reload();
            }
        }), a.rowLp());
        col.addView(a.listRow("Affiches des épisodes", new MainActivity.Value() {
            @Override public String get() { return a.prefs.shelfPosters() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setShelfPosters(!a.prefs.shelfPosters()); a.shelf.reload(); }
        }), a.rowLp());
        col.addView(a.listRow("Lueur de l'appli sélectionnée", new MainActivity.Value() {
            @Override public String get() { return a.prefs.appGlow() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setAppGlow(!a.prefs.appGlow()); }
        }), a.rowLp());
        col.addView(a.listRow("Sons de l'interface", new MainActivity.Value() {
            @Override public String get() { return a.prefs.sounds() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.prefs.setSounds(!a.prefs.sounds());
                Sounds.get(a).setEnabled(a.prefs.sounds());
            }
        }), a.rowLp());
        col.addView(a.listRow("Horloge", new MainActivity.Value() {
            @Override public String get() { return a.prefs.showClock() ? "Affichée" : "Masquée"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setShowClock(!a.prefs.showClock()); }
        }), a.rowLp());
        col.addView(a.listRow("Estomper l'interface au repos", new MainActivity.Value() {
            @Override public String get() { return a.prefs.idleFade() ? "Oui" : "Non"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) { a.prefs.setIdleFade(!a.prefs.idleFade()); }
        }), a.rowLp());

        col.addView(a.section("Système"));
        col.addView(a.listRow("Historique complet pour Reprendre", new MainActivity.Value() {
            @Override public String get() { return ResumePanel.usageAllowed(a) ? "Autorisé" : "Non autorisé"; }
        }, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ResumePanel.usageAllowed(a)) return;
                try {
                    Intent i = new Intent("android.settings.USAGE_ACCESS_SETTINGS");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                } catch (Exception e) { a.showNotice("À autoriser par ADB, voir le guide"); }
            }
        }), a.rowLp());
        col.addView(a.listRow("Paramètres de la TV", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                } catch (Exception e) { a.showNotice("Paramètres indisponibles"); }
            }
        }), a.rowLp());

        TextView about = a.text(19, "sans-serif", 0x66FFFFFF);
        about.setText("Lumen 4.0.1 · aucune publicité, aucun pistage · Internet utilisé uniquement pour les photos et vidéos NASA et Wikimedia Commons et la météo Open-Meteo");
        about.setPadding(a.u(32), a.u(36), a.u(32), 0);
        col.addView(about);
        first.requestFocus();
    }

    /** Choix des villes pour les vidéos aériennes. */
    static void fillCities(final MainActivity a, final LinearLayout card) {
        card.removeAllViews();
        a.rowRefreshers.clear();
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.u(24), a.u(48), a.u(24), a.u(48));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final boolean[] changed = {false};
        LinearLayout back = a.listRow("‹  Réglages", null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (changed[0]) a.online.refreshIfNeeded(Online.VIDEO, true);
        fillSettings(a, card);
            }
        });
        col.addView(back, a.rowLp());
        TextView title = a.text(40, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Villes");
        title.setPadding(a.u(32), a.u(24), 0, a.u(20));
        col.addView(title);
        for (String[] c : Online.CITIES) {
            final String name = c[0];
            col.addView(a.listRow(name, new MainActivity.Value() {
                @Override public String get() { return a.prefs.cities().contains(name) ? "✓" : ""; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) { a.prefs.setCity(name, !a.prefs.cities().contains(name)); changed[0] = true; }
            }), a.rowLp());
        }
        back.requestFocus();
    }

    static void fillHiddenApps(final MainActivity a, final LinearLayout card) {
        card.removeAllViews();
        a.rowRefreshers.clear();
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalFadingEdgeEnabled(true);
        sv.setFadingEdgeLength(a.u(70));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(a.u(24), a.u(48), a.u(24), a.u(48));
        sv.addView(col);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout back = a.listRow("‹  Réglages", null, new View.OnClickListener() {
            @Override public void onClick(View v) { fillSettings(a, card); }
        });
        col.addView(back, a.rowLp());
        TextView title = a.text(40, "sans-serif-light", 0xF5FFFFFF);
        title.setText("Applis masquées");
        title.setPadding(a.u(32), a.u(24), 0, a.u(8));
        col.addView(title);
        TextView hint = a.text(21, "sans-serif", 0x8CFFFFFF);
        hint.setText("OK pour réafficher l'appli");
        hint.setPadding(a.u(32), 0, 0, a.u(20));
        col.addView(hint);
        for (final String key : a.prefs.hiddenApps()) {
            AppRepo.App app = a.repo.get(key);
            final String name = app != null ? app.label : key;
            final LinearLayout[] holder = new LinearLayout[1];
            holder[0] = a.listRow(name, new MainActivity.Value() {
                @Override public String get() { return a.prefs.hiddenApps().contains(key) ? "Masquée" : "Visible"; }
            }, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean nowHidden = !a.prefs.hiddenApps().contains(key);
                    a.prefs.setAppHidden(key, nowHidden);
                    a.dirtyRow = true;
                    a.markAppsDirty();
                }
            });
            col.addView(holder[0], a.rowLp());
        }
        back.requestFocus();
    }

}
