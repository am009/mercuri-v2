int main(){
        int a = 0;
        int b = 1;
        while (1000 > b) {
            putint(b);
            int t = a;
            a = b;
            b = b + t;
        }
}