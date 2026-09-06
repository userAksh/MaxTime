import { httpsCallable } from "firebase/functions";
import { functions } from "./firebase";

export function callFunction<TReq extends object, TRes>(name: string) {
  const callable = httpsCallable<TReq, TRes>(functions, name);
  return (data: TReq) => callable(data).then((r) => r.data);
}
