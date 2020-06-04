<img height="80px" src="/resources/public/img/logo.svg">

>  _The only prescription, is more description._


__Descryptors__ tries to make finding and monitoring cryptocurrency projects easier. Our goal is to be as transparent and neutral as possible.

https://descryptors.io



## Building

To build __Descryptors__ you will need to install [Clojure CLI tools](https://clojure.org/guides/getting_started).


### Start Backend

```
clj -A:backend
```

After building the Clojure code, it will load the data files (if specified), start the webserver and a REPL on port `3311`. All these can be configured in `conf/config.edn`.

Descryptors uses [__Roll__](https://github.com/dimovich/roll) as a backend "framework".

### Start Frontend

```
clj -A:frontend
```

This will start Figwheel which will build the ClojureScript code.

Descryptors uses [__Proto__](https://github.com/descryptors/proto) for frontend React components.

### Open

After building is done, navigate in your browser to `localhost:5000`. It is possible to start the backend only with server-side rendering of the pages.


## Data

The project comes with some sample data located in `data` folder. This includes basic information about the cryptocurrency projects and a precomputed index for marketcap and code activity.

Descryptors doesn't collect data by itself. For that you need to use [__Destreams__](https://github.com/descryptors/destreams).


## Overview

<img src="/resources/overview.png">


## License

Copyright © 2020 Descryptors team

Licensed under GNU General Public License version 3 (see [LICENSE](LICENSE)).
