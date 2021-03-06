/*
Copyright (c) 2018 Joshua Sander
This work is available under the "MIT License”.
Please see the file LICENSE in this distribution
for license terms.
*/

//Used the following site for transfering data between activities:
//https://stackoverflow.com/questions/5265913/how-to-use-putextra-and-getextra-for-string-data

package com.example.joshuasander.moviehaters;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private String api = "";
    private final String awsEC2 = "http://ec2-13-59-63-149.us-east-2.compute.amazonaws.com:3000";
    private String userName;
    private boolean newPage;
    private String currentReview = null;
    private String movieId;
    private double[] aggregates = new double[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if(extras == null) {
                userName = "josh";
            } else {
                userName = extras.getString("uname");
            }
        } else {
            userName = (String) savedInstanceState.getSerializable("uname");
        }

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try  {
                    api = connect("/api");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    public void getOmdbApi(View view) throws IOException {
        EditText editText = (EditText) findViewById(R.id.editText);
        String userSelection = editText.getText().toString();
        userSelection = userSelection.replaceAll("\\s+","_");
        final String message = api + "&t=" + userSelection;

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try  {
                    //Used source:
                    //https://medium.com/@yossisegev/understanding-activity-runonuithread-e102d388fe93

                    final String fullResponse = connectOmdb(message);
                    final String movieDetails = parseIt(fullResponse);
                    float rating = -1;
                    String review = null;

                    if (movieDetails == null) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                ((TextView) findViewById(R.id.mainText)).setVisibility(View.VISIBLE);
                                ((TextView) findViewById(R.id.mainText)).setText("Movie not Found!");
                                ((RatingBar)findViewById(R.id.ratingBar)).setVisibility(View.INVISIBLE);
                                ((TextView) findViewById(R.id.review)).setVisibility(View.INVISIBLE);
                                ((Button) findViewById(R.id.reviewStatus)).setVisibility(View.INVISIBLE);
                                ((TextView) findViewById(R.id.user1)).setVisibility(View.INVISIBLE);
                                ((TextView) findViewById(R.id.review1)).setVisibility(View.INVISIBLE);
                                ((TextView) findViewById(R.id.user2)).setVisibility(View.INVISIBLE);
                                ((TextView) findViewById(R.id.review2)).setVisibility(View.INVISIBLE);
                            }
                        });
                        currentReview = null;
                        return;
                    }

                    movieId = parseId(fullResponse);
                    getFriends(userName, movieId, aggregates);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            try {
                                ((TextView) findViewById(R.id.aggregateInfo)).setVisibility(View.VISIBLE);
                                ((TextView) findViewById(R.id.aggregateInfo)).setText(aggregrateResult(aggregates));
                            } catch (Exception e) {e.printStackTrace();}
                        }
                    });

                    runOnUiThread(new Runnable() {
                        public void run() {
                            ((RatingBar)findViewById(R.id.ratingBar)).setVisibility(View.VISIBLE);
                            ((TextView) findViewById(R.id.review)).setVisibility(View.VISIBLE);
                            ((Button) findViewById(R.id.reviewStatus)).setVisibility(View.VISIBLE);
                            ((TextView) findViewById(R.id.mainText)).setVisibility(View.VISIBLE);

                            currentReview = "";

                            RatingBar ratingBar = (RatingBar) findViewById(R.id.ratingBar);
                            ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
                                @Override
                                public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                                    final float finalRating = rating;
                                    Thread thread = new Thread(new Runnable() {

                                        @Override
                                        public void run() {
                                            try  {
                                                String check = connect("/check?name=" + userName + "&id=" + movieId);
                                                if (check.equals("[]")) {
                                                    connect("/insertReview?name=" + userName + "&id=" + movieId + "&rating=" + finalRating);
                                                }
                                                else {
                                                    connect("/update?name=" + userName + "&id=" + movieId + "&rating=" + finalRating);
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });

                                    thread.start();
                                }
                            });
                        }
                    });

                    final String yourData = getRatingFromServer(parseId(fullResponse), awsEC2);
                    if (yourData != null && yourData.equals("null") == false && yourData.equals("[]") == false) {
                        rating = parseRating(yourData);
                        review = parseReview(yourData);

                    }

                    if (rating != -1) {
                        final float ratingResult = rating;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                try {
                                    ((RatingBar) findViewById(R.id.ratingBar)).setRating(ratingResult);
                                    ((TextView) findViewById(R.id.review)).setText(parseReview(yourData));
                                    //Sourced from:
                                    //https://stackoverflow.com/questions/4602902/how-to-set-the-text-color-of-textview-in-code
                                    ((TextView) findViewById(R.id.review)).setTextColor(Color.RED);
                                    ((TextView) findViewById(R.id.review)).setMovementMethod(new ScrollingMovementMethod());
                                } catch (Exception e) {e.printStackTrace();}
                            }
                        });
                    }
                    else {

                    }

                    if (review != null && review.equals("null") == false && review.equals("[]") == false) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                try {
                                    ((Button) findViewById(R.id.reviewStatus)).setText("Edit Review");
                                    ((TextView) findViewById(R.id.review)).setText(parseReview(yourData));
                                    //Sourced from:
                                    //https://stackoverflow.com/questions/4602902/how-to-set-the-text-color-of-textview-in-code
                                    ((TextView) findViewById(R.id.review)).setTextColor(Color.RED);
                                    ((TextView) findViewById(R.id.review)).setMovementMethod(new ScrollingMovementMethod());
                                    currentReview = parseReview(yourData);
                                } catch (Exception e) {e.printStackTrace();}
                            }
                        });
                    }
                    else {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                ((Button) findViewById(R.id.reviewStatus)).setText("Add Review");
                                ((TextView) findViewById(R.id.review)).setTextColor(Color.RED);
                                ((TextView)findViewById(R.id.review)).setText("No review");
                            }
                        });
                    }

                    runOnUiThread(new Runnable() {
                        public void run() {
                            ((TextView)findViewById(R.id.mainText)).setText(movieDetails);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();

    }

    public String parseIt(String input) throws Exception {

        JSONObject jsonObject = new JSONObject(input);

        if (jsonObject.getString("Response").equals("False")) {
            return null;
        }

        String result = jsonObject.getString("Title") + "\n";
        result += "year: " + jsonObject.getString("Year") + "\n";
        result += "imdb rating: " + jsonObject.getString("imdbRating") + "\n";

        String poster = jsonObject.getString("Poster");
        loadImage(poster);

        return result;

    }

    public float parseRating(String input) throws Exception {
        if (input.equals("[]")){
            return -1;
        }
        //Source:
        //https://stackoverflow.com/questions/7438612/how-to-remove-the-last-character-from-a-string
        input = input.substring(1, input.length() - 1);

        JSONObject jsonObject = new JSONObject(input);

        String result = jsonObject.getString("stars");

        return Float.parseFloat(result);
    }

    public String parseReview(String input) throws Exception {
        if (input.equals("[]")){
            return null;
        }
        //Source:
        //https://stackoverflow.com/questions/7438612/how-to-remove-the-last-character-from-a-string
        input = input.substring(1, input.length() - 1);

        JSONObject jsonObject = new JSONObject(input);

        return jsonObject.getString("review");
    }

    public String parseId(String input) throws Exception {

        return new JSONObject(input).getString("imdbID");
    }

    public void loadImage (String url) {
        try {
            InputStream is = (InputStream) new URL(url).getContent();
            Drawable drawable = Drawable.createFromStream(is, "src name");
            ImageView img= (ImageView) findViewById(R.id.poster);
            img.setImageDrawable(drawable);
            return;
        } catch (Exception e) {
            return;
        }
    }

    public String getRatingFromServer(String id, String server) throws Exception {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(server + "/data?name=" + userName + "&id=" + id);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            InputStream stream = connection.getInputStream();

            reader = new BufferedReader(new InputStreamReader(stream));

            StringBuffer buffer = new StringBuffer();
            String line = "";

            buffer.append(reader.readLine());

            return buffer.toString();
    } catch(
    MalformedURLException e)

    {
        e.printStackTrace();
    } catch(
    IOException e)

    {
        e.printStackTrace();
    } finally

    {
        if (connection != null) {
            connection.disconnect();
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        }
        return null;
    }

    public String connect(String input) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try
        {
            URL url = new URL(awsEC2 + input);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            InputStream stream = connection.getInputStream();

            reader = new BufferedReader(new InputStreamReader(stream));

            StringBuffer buffer = new StringBuffer();
            String line = "";

            buffer.append(reader.readLine());

            return buffer.toString();


        } catch(
                MalformedURLException e)

        {
            e.printStackTrace();
        } catch(
                IOException e)

        {
            e.printStackTrace();
        } finally

        {
            if (connection != null) {
                connection.disconnect();
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public String connectOmdb(String input) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try
        {
            URL url = new URL(input);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            InputStream stream = connection.getInputStream();

            reader = new BufferedReader(new InputStreamReader(stream));

            StringBuffer buffer = new StringBuffer();
            String line = "";

            buffer.append(reader.readLine());

            return buffer.toString();


        } catch(
                MalformedURLException e)

        {
            e.printStackTrace();
        } catch(
                IOException e)

        {
            e.printStackTrace();
        } finally

        {
            if (connection != null) {
                connection.disconnect();
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public void reviewPage(View view) {

        if (currentReview == null) {
            return;
        }

        Intent intent = new Intent(MainActivity.this, EditReview.class);
        intent.putExtra("uname", userName);
        intent.putExtra("currentReview", currentReview);
        intent.putExtra("movieId", movieId);
        startActivity(intent);
    }

    public void getFriends(String name, String id, double [] array) throws Exception{
        ArrayList<Review> reviews = new ArrayList<>();

        String path = "/aggregate";
        String path2 = "/aggregate2";

        double starsTotal   = 0;
        double starsTaste   = 0;
        double starsBad     = 0;
        int count           = 0;
        int countTaste      = 0;
        int countBad        = 0;

        String result = connect(path + "?" + "name=" + name + "&id=" + id);
        String [] friends = result.split(",");
        String [] friendsResults;
        String test;

        for (int x = 0; x < friends.length; x+=2) {
            test = connect(path2 + "?" + "name=" + friends[x] + "&id=" + id);
            friendsResults = parseFriendsReview(test);

            if (friendsResults == null) {
                continue;
            }

            reviews.add(new Review(friends[x], Double.parseDouble(friendsResults[0]), friendsResults[1]));

            starsTotal += Double.parseDouble(friendsResults[0]);
            count++;

            if (friends[x + 1].equals("1")) {
                starsBad += Double.parseDouble(friendsResults[0]);
                countBad++;
            }
            else if (friends[x + 1].equals("2")) {
                starsTaste += Double.parseDouble(friendsResults[0]);
                countTaste++;
            }
        }

        if (count != 0) {
            starsTotal /= count;
        }
        if (countTaste != 0) {
            starsTaste /= countTaste;
        }
        if (countBad != 0) {
            starsBad /= countBad;
        }

        array[0] = starsTotal;
        array[1] = starsTaste;
        array[2] = starsBad;

        if (reviews.size() > 0) {
            setReviewPanels(reviews);
        }
        else {
            clearReviewPanels();
        }
    }

    public String [] parseFriendsReview(String input) throws Exception {
        if (input.equals("[]")){
            return null;
        }

        input = input.substring(1, input.length() - 1);

        JSONObject jsonObject = new JSONObject(input);


        String result = jsonObject.getString("stars");
        String result2 = jsonObject.getString("review");

        String [] finalResult = new String [] {result, result2};

        return finalResult;
    }

    public String aggregrateResult(double [] array) {

        String result = "  Avg friend: ";
        if (aggregates[0] == 0) {
            result += "N/A";
        }
        else {
            result += aggregates[0];
        }

        result += "   Good tasters: ";
        if (aggregates[1] == 0) {
            result += "N/A";
        }
        else {
            result += aggregates[1];
        }

        result += "   Bad tasters: ";
        if (aggregates[2] == 0) {
            result += "N/A";
        }
        else {
            result += aggregates[2];
        }

        return result;
    }

    public void setReviewPanels(final ArrayList<Review> reviews) {
        runOnUiThread(new Runnable() {
            public void run() {
                for (int x = 0; x < reviews.size();x++) {
                    if (x == 0) {
                        ((TextView) findViewById(R.id.user1)).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.review1)).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.user1)).setText(reviews.get(x).getUname() + "\n" + reviews.get(x).getRating());
                        ((TextView) findViewById(R.id.review1)).setText(reviews.get(x).getReview());
                    }
                    if (x == 1) {
                        ((TextView) findViewById(R.id.user2)).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.review2)).setVisibility(View.VISIBLE);
                        ((TextView) findViewById(R.id.user2)).setText(reviews.get(x).getUname() + "\n" + reviews.get(x).getRating());
                        ((TextView) findViewById(R.id.review2)).setText(reviews.get(x).getReview());
                    }

                }
            }
        });
    }

    public void clearReviewPanels() {
        runOnUiThread(new Runnable() {
            public void run() {
                        ((TextView) findViewById(R.id.user1)).setVisibility(View.INVISIBLE);
                        ((TextView) findViewById(R.id.review1)).setVisibility(View.INVISIBLE);
                        ((TextView) findViewById(R.id.user2)).setVisibility(View.INVISIBLE);
                        ((TextView) findViewById(R.id.review2)).setVisibility(View.INVISIBLE);

            }
        });
    }

}
