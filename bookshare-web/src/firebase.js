import { initializeApp } from 'firebase/app'
import { getAuth, GoogleAuthProvider } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

// Your web app's Firebase configuration
const firebaseConfig = {
  apiKey: "AIzaSyAIOZgjk6OWNZ6ZTJrTScbSadpMdsQ3kjY",
  authDomain: "bookshare-cd748.firebaseapp.com",
  projectId: "bookshare-cd748",
  storageBucket: "bookshare-cd748.firebasestorage.app",
  messagingSenderId: "442161051683",
  appId: "1:442161051683:web:e3369ea739d388f95a1996"
};

const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const db = getFirestore(app)
export const googleProvider = new GoogleAuthProvider()