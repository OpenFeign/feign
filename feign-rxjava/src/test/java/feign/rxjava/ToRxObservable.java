package feign.rxjava;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.functions.Func1;

public class ToRxObservable {

  private ToRxObservable() {
  }

  public static <T> Observable<T> convert(final feign.Observable<T> observable) {
    Func1<Observer<T>, Subscription> onSubscribe = new Func1<Observer<T>, Subscription>() {

      @Override public Subscription call(Observer<T> observer) {
        return toRx(observable.subscribe(toFeign(observer)));
      }
    };
    return new Observable<T>(onSubscribe) {
    };
  }

  private static <T> feign.Observer<T> toFeign(final Observer<T> observer) {
    return new feign.Observer<T>() {

      @Override public void onNext(T element) {
        observer.onNext(element);
      }

      @Override public void onSuccess() {
        observer.onCompleted();
      }

      @Override public void onFailure(Throwable cause) {
        observer.onError(cause);
      }
    };
  }

  private static Subscription toRx(final feign.Subscription subscription) {
    return new Subscription() {
      @Override public void unsubscribe() {
        subscription.unsubscribe();
      }
    };
  }
}
