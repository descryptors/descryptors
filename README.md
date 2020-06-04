<img height="80px" src="/resources/logo.svg">

>  _The only prescription, is more description._


__Descryptors__ tries to make finding and monitoring cryptocurrency projects easier. Our goal is to be as transparent and neutral as possible.


## Building

To build __Descryptors__ you will need to install [Clojure CLI tools](https://clojure.org/guides/getting_started).


### Start Backend

```
clj -A:backend
```

After building the Clojure code, it will load the data files (if specified), start the webserver and a REPL on port `3311`. All these can be configured in `conf/config.edn`.

### Start Frontend

```
clj -A:frontend
```

This will start Figwheel which will build the ClojureScript code.

### Open

After building is done, navigate in your browser to `localhost:5000`. It is possible to start the backend only with server-side rendering of the pages.


## Data

The project comes with some sample data located in `data` folder. This includes basic information about the cryptocurrency projects and a precomputed index for marketcap and code activity.




## License

Copyright © 2020 Descryptors team

Licensed under GNU General Public License version 3 (see [LICENSE](LICENSE)).
