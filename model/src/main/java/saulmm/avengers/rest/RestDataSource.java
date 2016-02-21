/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package saulmm.avengers.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import java.util.List;
import javax.inject.Inject;
import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import rx.Observable;
import rx.functions.Func1;
import saulmm.avengers.rest.entities.RestCollectionItem;
import saulmm.avengers.rest.entities.RestCharacter;
import saulmm.avengers.repository.CharacterRepository;
import saulmm.avengers.rest.exceptions.ServerErrorException;
import saulmm.avengers.rest.exceptions.UknownErrorException;
import saulmm.avengers.rest.utils.deserializers.MarvelResultsDeserializer;
import saulmm.avengers.rest.utils.interceptors.MarvelSigningIterceptor;

import static com.squareup.okhttp.logging.HttpLoggingInterceptor.*;
import static saulmm.avengers.rest.entities.RestCollectionItem.COMICS;
import static saulmm.avengers.rest.entities.RestCollectionItem.EVENTS;
import static saulmm.avengers.rest.entities.RestCollectionItem.SERIES;
import static saulmm.avengers.rest.entities.RestCollectionItem.STORIES;

public class RestDataSource implements CharacterRepository {
    public static String END_POINT       = "http://gateway.marvel.com/";
    public static String PARAM_API_KEY   = "apikey";
    public static String PARAM_HASH      = "hash";
    public static String PARAM_TIMESTAMP = "ts";

    private final MarvelApi mMarvelApi;
    public final static int MAX_ATTEMPS = 3;

    @Inject
    public RestDataSource(MarvelAuthorizer marvelAuthorizer) {
        OkHttpClient client = new OkHttpClient();

        MarvelSigningIterceptor signingIterceptor =
            new MarvelSigningIterceptor(
                marvelAuthorizer.getApiClient(),
                marvelAuthorizer.getApiSecret());

        HttpLoggingInterceptor logginInterceptor = new HttpLoggingInterceptor();
        logginInterceptor.setLevel(Level.BODY);

        client.interceptors().add(signingIterceptor);
        client.interceptors().add(logginInterceptor);

        Gson customGsonInstance = new GsonBuilder()
            .registerTypeAdapter(new TypeToken<List<RestCharacter>>() {}.getType(),
                new MarvelResultsDeserializer<RestCharacter>())

            .registerTypeAdapter(new TypeToken<List<RestCollectionItem>>() {}.getType(),
                new MarvelResultsDeserializer<RestCollectionItem>())

            .create();

        Retrofit marvelApiAdapter = new Retrofit.Builder()
            .baseUrl(END_POINT)
            .addConverterFactory(GsonConverterFactory.create(customGsonInstance))
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .client(client)
            .build();

        mMarvelApi =  marvelApiAdapter.create(MarvelApi.class);
    }

	@Override
    public Observable<RestCharacter> getCharacter(final int characterId) {
        return mMarvelApi.getCharacterById(characterId)
                .flatMap(new Func1<List<RestCharacter>, Observable<RestCharacter>>() {
                   @Override public Observable<RestCharacter> call(List<RestCharacter> characters) {
                       return Observable.just(characters.get(0));
                   }
               });
	}

    @Override
    public Observable<List<RestCharacter>> getCharacters(int currentOffset) {
        return mMarvelApi.getCharacters(currentOffset)
            .onErrorResumeNext(new Func1<Throwable, Observable<? extends List<RestCharacter>>>() {
                @Override
                public Observable<? extends List<RestCharacter>> call(Throwable throwable) {
                    boolean serverError = throwable.getMessage().equals(HttpErrors.SERVER_ERROR);
                    return Observable.error(
                            (serverError) ? new ServerErrorException() : new UknownErrorException());
                }
            });
    }

    @Override
    public Observable<List<RestCollectionItem>> getCharacterCollection(int characterId, String type) {
        if (!type.equals(COMICS) && !type.equals(EVENTS) && !type.equals(SERIES) && !type.equals(STORIES))
            throw new IllegalArgumentException("Collection type must be: events|series|comics|stories");

        return mMarvelApi.getCharacterCollection(characterId, type);
    }
}
